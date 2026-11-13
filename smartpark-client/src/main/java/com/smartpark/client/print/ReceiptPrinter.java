package com.smartpark.client.print;

import com.smartpark.client.parking.ReceiptData;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.transform.Scale;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prints receipts with the JavaFX printing API ({@link PrinterJob}), which drives any printer
 * installed in Windows: office printers on A4/Letter and thermal receipt printers on 80 or 58 mm
 * rolls. No browser, PDF step or JavaScript is involved.
 * <p>
 * Every problem is reported as a {@link PrintFailure} with an operator-friendly message: no
 * printer installed, printer removed, printer offline or paused, or the job failing.
 * <p>
 * Call the methods on the JavaFX Application Thread.
 */
public final class ReceiptPrinter {

    private static final Logger LOG = Logger.getLogger(ReceiptPrinter.class.getName());

    /** What was printed, where. */
    public record PrintResult(String printerName, PaperSize paperSize, String paperName) {
    }

    /** Names of the printers installed in Windows, sorted. */
    public List<String> printerNames() {
        return Printer.getAllPrinters().stream().map(Printer::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** The Windows default printer, if one is set. */
    public Optional<String> defaultPrinterName() {
        Printer p = Printer.getDefaultPrinter();
        return p == null ? Optional.empty() : Optional.of(p.getName());
    }

    /**
     * Resolves {@link PaperSize#AUTO} from the printer's default paper: a roll of about 58 or
     * 80 mm means a thermal receipt printer, anything wider is standard paper.
     */
    public PaperSize resolve(PaperSize requested, String printerName) {
        if (requested != PaperSize.AUTO) {
            return requested;
        }
        return find(printerName)
                .map(p -> PaperSize.forPaperWidth(p.getPrinterAttributes().getDefaultPaper().getWidth()))
                .orElse(PaperSize.STANDARD);
    }

    /**
     * Prints one receipt.
     *
     * @param copyNumber 0 for the original, 1, 2 ... for reprints (adds the duplicate marker)
     */
    public PrintResult print(ReceiptData data, ReceiptTemplate template, int copyNumber,
                             String printerName, PaperSize requested) throws PrintFailure {
        if (Printer.getAllPrinters().isEmpty()) {
            throw new PrintFailure(PrintFailure.Reason.NO_PRINTER,
                    "No printer is installed on this computer. Install the receipt printer in Windows "
                            + "(Settings > Printers & scanners) and try again.");
        }
        if (printerName == null || printerName.isBlank()) {
            throw new PrintFailure(PrintFailure.Reason.PRINTER_NOT_FOUND, "Choose a printer first.");
        }
        Printer printer = find(printerName).orElseThrow(() -> new PrintFailure(PrintFailure.Reason.PRINTER_NOT_FOUND,
                "The printer \"" + printerName + "\" is no longer available. Choose another printer."));
        Optional<String> offline = offlineReason(printerName);
        if (offline.isPresent()) {
            throw new PrintFailure(PrintFailure.Reason.PRINTER_OFFLINE, "The printer \"" + printerName + "\" "
                    + offline.get() + ". Check that it is switched on, connected and has paper, then try again.");
        }

        PaperSize size = resolve(requested, printerName);
        ReceiptView view = new ReceiptView(data, template, size, copyNumber);
        // Lay the node out in an off-screen scene so its real size is known before printing.
        Group root = new Group(view);
        new Scene(root);
        view.applyCss();
        view.layout();
        double w = view.prefWidth(-1);
        double h = view.prefHeight(w);
        view.resize(w, h);
        view.layout();

        PageLayout layout = pageLayout(printer, size, h);
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            throw new PrintFailure(PrintFailure.Reason.JOB_FAILED,
                    "Could not start a print job on \"" + printerName + "\". The printer may be busy or unavailable.");
        }
        JobSettings settings = job.getJobSettings();
        settings.setJobName("SmartPark receipt " + data.ticketId());
        settings.setPageLayout(layout);
        settings.setCopies(1);

        // Fit the printable area (never enlarge); centre on standard paper.
        double pw = layout.getPrintableWidth();
        double ph = layout.getPrintableHeight();
        double scale = Math.min(1.0, Math.min(pw / w, ph / h));
        if (scale < 1.0) {
            view.getTransforms().add(new Scale(scale, scale, 0, 0));
        }
        if (!size.isThermal()) {
            view.setTranslateX(Math.max(0, (pw - w * scale) / 2));
        }

        try {
            if (!job.printPage(layout, view)) {
                job.cancelJob();
                throw new PrintFailure(PrintFailure.Reason.JOB_FAILED,
                        "The printer \"" + printerName + "\" did not accept the receipt (" + job.getJobStatus() + ").");
            }
            if (!job.endJob()) {
                throw new PrintFailure(PrintFailure.Reason.JOB_FAILED,
                        "Printing on \"" + printerName + "\" failed (" + job.getJobStatus() + ").");
            }
        } catch (PrintFailure e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Printing failed", e);
            job.cancelJob();
            throw new PrintFailure(PrintFailure.Reason.JOB_FAILED,
                    "Printing on \"" + printerName + "\" failed: " + e.getMessage(), e);
        }
        return new PrintResult(printerName, size, layout.getPaper().getName());
    }

    private static Optional<Printer> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Printer.getAllPrinters().stream().filter(p -> p.getName().equals(name)).findFirst();
    }

    /**
     * Thermal sizes use the printer's matching roll paper when the driver offers one (the
     * shortest that fits the receipt, so the printer feeds as little paper as possible); otherwise
     * the printer's default paper.
     */
    private static PageLayout pageLayout(Printer printer, PaperSize size, double contentHeight) {
        Paper paper = printer.getPrinterAttributes().getDefaultPaper();
        if (size.isThermal()) {
            List<Paper> rolls = printer.getPrinterAttributes().getSupportedPapers().stream()
                    .filter(p -> Math.abs(p.getWidth() - PaperSize.mm(size.rollWidthMm())) <= PaperSize.mm(6))
                    .sorted(Comparator.comparingDouble(Paper::getHeight))
                    .toList();
            if (!rolls.isEmpty()) {
                paper = rolls.stream().filter(p -> p.getHeight() >= contentHeight + PaperSize.mm(15))
                        .findFirst().orElse(rolls.getLast());
            }
        }
        try {
            return printer.createPageLayout(paper, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        } catch (RuntimeException e) {
            return printer.getDefaultPageLayout();
        }
    }

    /**
     * Asks Windows (through the Java Print Service API) whether the printer accepts jobs, so an
     * offline printer is reported before the job is sent. Unknown state counts as OK. Note that a
     * queue paused by hand in Windows is still reported as accepting: Windows holds the job and
     * prints it when the queue is resumed.
     */
    static Optional<String> offlineReason(String printerName) {
        try {
            for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
                if (!service.getName().equals(printerName)) {
                    continue;
                }
                PrinterIsAcceptingJobs accepting = service.getAttribute(PrinterIsAcceptingJobs.class);
                if (accepting == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
                    return Optional.of("is offline or not accepting print jobs");
                }
                PrinterState state = service.getAttribute(PrinterState.class);
                if (state == PrinterState.STOPPED) {
                    return Optional.of("has stopped");
                }
                return Optional.empty();
            }
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Printer state unavailable", e);
        }
        return Optional.empty();
    }
}
