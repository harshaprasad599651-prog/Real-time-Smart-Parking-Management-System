package com.smartpark.client.ui.parking;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.parking.ReceiptData;
import com.smartpark.client.print.PaperSize;
import com.smartpark.client.print.PrintFailure;
import com.smartpark.client.print.ReceiptPrinter;
import com.smartpark.client.print.ReceiptTemplate;
import com.smartpark.client.print.ReceiptView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;

/**
 * Receipt preview and printing.
 * <p>
 * Shown after a successful allocation (built from the server's 201 response) and for a reprint
 * by ticket ID (built from the ticket stored on the server). The operator sees the receipt
 * exactly as it will print, chooses the printer and paper (thermal 80/58 mm or standard), and
 * prints. Printer problems are shown in the dialog and the operator can fix them and retry; the
 * parking session is never affected. After a successful print the server records it, and the
 * next print of the same ticket is marked as a duplicate.
 */
final class ReceiptDialog {

    private final AppContext context;
    private final ReceiptData data;
    private final ReceiptTemplate template = ReceiptTemplate.load(); // re-read: edits apply without restart
    private final ReceiptPrinter printer;
    private final Stage stage = new Stage();
    private final ComboBox<String> printerBox = new ComboBox<>();
    private final ComboBox<PaperSize> paperBox = new ComboBox<>(FXCollections.observableArrayList(PaperSize.values()));
    private final Label detected = new Label();
    private final Label status = new Label();
    private final StackPane paper = new StackPane();
    private final Button printButton = new Button("Print receipt");
    private int printCount;
    /** True from "Print" until the server has recorded the print: blocks a second, unmarked print. */
    private boolean printing;

    private ReceiptDialog(AppContext context, ReceiptData data, int printCount) {
        this.context = context;
        this.data = data;
        this.printCount = printCount;
        this.printer = context.receiptPrinter();
    }

    /**
     * @param printCount how often this ticket was printed before (0 for a new ticket)
     * @param reprint    true when opened from "Reprint receipt" (changes the heading only)
     * @param onClosed   run when the dialog closes (e.g. focus the form for the next vehicle)
     */
    static void show(Window owner, AppContext context, ReceiptData data, int printCount, boolean reprint,
                     Runnable onClosed) {
        new ReceiptDialog(context, data, printCount).open(owner, reprint, onClosed);
    }

    private void open(Window owner, boolean reprint, Runnable onClosed) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle((reprint ? "Reprint receipt - " : "Parking receipt - ") + data.ticketId());
        stage.setOnHidden(e -> onClosed.run());

        Label heading = new Label(reprint
                ? "Reprint of ticket " + data.ticketId() + (printCount > 0 ? " (printed " + times(printCount) + " before)" : "")
                : "Vehicle parked. Receipt ready for ticket " + data.ticketId());
        heading.getStyleClass().add(reprint ? "info-banner" : "success-banner");
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setWrapText(true);

        // ---- preview
        paper.getStyleClass().add("receipt-paper");
        paper.setMaxWidth(Region.USE_PREF_SIZE);
        paper.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane centred = new StackPane(paper);
        centred.setAlignment(Pos.TOP_CENTER);
        centred.setPadding(new Insets(12));
        ScrollPane previewScroll = new ScrollPane(centred);
        previewScroll.setFitToWidth(true);
        previewScroll.setPrefViewportWidth(340);
        previewScroll.setPrefViewportHeight(470);
        previewScroll.getStyleClass().add("receipt-preview");
        Label previewCaption = new Label("PREVIEW");
        previewCaption.getStyleClass().add("stat-caption");
        VBox preview = new VBox(6, previewCaption, previewScroll);

        // ---- printer settings
        List<String> printers = printer.printerNames();
        printerBox.setItems(FXCollections.observableArrayList(printers));
        printerBox.setMaxWidth(Double.MAX_VALUE);
        printerBox.setPromptText("No printer installed");
        String preferred = context.printSettings().printerName();
        if (preferred != null && printers.contains(preferred)) {
            printerBox.setValue(preferred);
        } else {
            printer.defaultPrinterName().filter(printers::contains).ifPresent(printerBox::setValue);
            if (printerBox.getValue() == null && !printers.isEmpty()) {
                printerBox.setValue(printers.getFirst());
            }
        }
        paperBox.setValue(context.printSettings().paper());
        paperBox.setMaxWidth(Double.MAX_VALUE);
        detected.getStyleClass().add("muted");
        detected.setWrapText(true);
        // ENTER confirms a choice in the list; it must not also press the default "Print" button.
        for (ComboBox<?> box : List.of(printerBox, paperBox)) {
            box.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    e.consume();
                }
            });
        }
        printerBox.valueProperty().addListener((o, a, b) -> refreshPreview());
        paperBox.valueProperty().addListener((o, a, b) -> refreshPreview());

        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        hideStatus();

        Label printerLabel = new Label("Printer");
        printerLabel.getStyleClass().add("field-label");
        Label paperLabel = new Label("Paper");
        paperLabel.getStyleClass().add("field-label");
        Label hint = new Label("Thermal receipt printers use 80 mm or 58 mm rolls. "
                + "Office printers print the receipt at the top of an A4 or Letter page.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        VBox settings = new VBox(8, printerLabel, printerBox, paperLabel, paperBox, detected, hint, status);
        settings.setPrefWidth(290);
        settings.setMinWidth(260);

        // ---- buttons
        Button close = new Button("Close");
        close.getStyleClass().add("secondary-button");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        printButton.getStyleClass().add("primary-button");
        printButton.setDefaultButton(true);
        printButton.setOnAction(e -> print());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, close, printButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        settings.getChildren().addAll(new Region(), buttons);
        VBox.setVgrow(settings.getChildren().get(settings.getChildren().size() - 2), Priority.ALWAYS);

        HBox body = new HBox(20, preview, settings);
        VBox root = new VBox(16, heading, body);
        root.setPadding(new Insets(20));
        root.getStyleClass().addAll("receipt-dialog", "dialog-root");

        if (printers.isEmpty()) {
            showStatus("warning-banner", "No printer is installed on this computer. You can still check the "
                    + "receipt here; install the receipt printer in Windows to print it.");
        }
        refreshPreview();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/smartpark.css")).toExternalForm());
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        printButton.requestFocus();
    }

    /** Redraws the preview for the chosen paper (AUTO resolved from the chosen printer). */
    private void refreshPreview() {
        PaperSize requested = paperBox.getValue() == null ? PaperSize.AUTO : paperBox.getValue();
        PaperSize size = printer.resolve(requested, printerBox.getValue());
        detected.setText(requested == PaperSize.AUTO ? "Detected: " + size.displayName() : "");
        detected.setVisible(requested == PaperSize.AUTO);
        detected.setManaged(requested == PaperSize.AUTO);
        // On screen 1 pt is drawn as 1 px: thermal layouts are shown 1.3x larger so they stay readable.
        ReceiptView view = new ReceiptView(data, template, size, printCount);
        double zoom = size.isThermal() ? 1.3 : 1.0;
        view.setScaleX(zoom);
        view.setScaleY(zoom);
        paper.getChildren().setAll(new Group(view));
        printButton.setText(printCount > 0 ? "Print duplicate" : "Print receipt");
        printButton.setDisable(printing || printerBox.getItems().isEmpty());
    }

    private void print() {
        if (printing) {
            return;
        }
        printing = true;
        String printerName = printerBox.getValue();
        PaperSize requested = paperBox.getValue() == null ? PaperSize.AUTO : paperBox.getValue();
        printButton.setDisable(true);
        showStatus("info-banner", "Sending the receipt to " + (printerName == null ? "the printer" : printerName) + "...");
        // Let the status paint before the (briefly blocking) print job runs on the FX thread.
        Platform.runLater(() -> {
            try {
                ReceiptPrinter.PrintResult result = printer.print(data, template, printCount, printerName, requested);
                onPrinted(result);
            } catch (PrintFailure failure) {
                printing = false;
                showStatus("error-banner", failure.getMessage());
                printButton.setDisable(printerBox.getItems().isEmpty());
            }
        });
    }

    /** The printer accepted the job: record it on the server (the next print becomes a duplicate). */
    private void onPrinted(ReceiptPrinter.PrintResult result) {
        String where = result.printerName() + " (" + result.paperSize().displayName() + ")";
        showStatus("success-banner", "Receipt sent to " + where + ".");
        context.parking().recordPrint(data.ticketId(), result.printerName())
                .whenComplete((receipt, error) -> Platform.runLater(() -> {
                    printing = false;
                    if (error == null) {
                        printCount = receipt.printCount();
                    } else {
                        printCount++;
                        ApiException e = ApiClient.unwrap(error);
                        if (!e.isUnauthorized()) {
                            showStatus("warning-banner", "Receipt sent to " + where + ", but the server could not "
                                    + "record the print: " + e.userMessage());
                        }
                    }
                    refreshPreview();
                }));
    }

    private void showStatus(String styleClass, String text) {
        status.getStyleClass().removeAll("info-banner", "success-banner", "warning-banner", "error-banner");
        status.getStyleClass().add(styleClass);
        status.setText(text);
        status.setVisible(true);
        status.setManaged(true);
    }

    private void hideStatus() {
        status.setVisible(false);
        status.setManaged(false);
    }

    private static String times(int n) {
        return n == 1 ? "once" : n + " times";
    }
}
