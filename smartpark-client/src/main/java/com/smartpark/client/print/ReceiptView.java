package com.smartpark.client.print;

import com.smartpark.client.parking.ReceiptData;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

import java.time.ZoneId;
import java.util.Locale;

/**
 * The receipt as a JavaFX node. The <b>same</b> node type is shown in the preview and sent to the
 * printer, so the preview matches the print-out.
 * <p>
 * The text comes from a {@link ReceiptTemplate}; the size from a {@link PaperSize} (layout width
 * in points and a font scale, so 58 mm and 80 mm thermal rolls get a layout made for them).
 * Styles are inline and black-on-white, so the node prints identically without the application
 * stylesheet and stays sharp on monochrome thermal printers.
 */
public final class ReceiptView extends VBox {

    private final double scale;

    /**
     * @param size       a concrete size (not {@link PaperSize#AUTO})
     * @param copyNumber 0 for the original receipt, 1, 2 ... for reprints
     */
    public ReceiptView(ReceiptData data, ReceiptTemplate template, PaperSize size, int copyNumber) {
        if (size == PaperSize.AUTO) {
            throw new IllegalArgumentException("Resolve AUTO to a concrete paper size first");
        }
        this.scale = size.fontScale();
        double width = size.contentWidth();
        double pad = 14 * scale;
        setSpacing(7 * scale);
        setAlignment(Pos.TOP_CENTER);
        setMinWidth(width);
        setPrefWidth(width);
        setMaxWidth(width);
        setStyle("-fx-background-color: white; -fx-padding: " + pad + " " + pad + " " + pad + " " + pad + ";"
                + " -fx-font-family: 'Segoe UI', Arial, sans-serif;");

        getChildren().add(text(template.title(), 22, true, "#000000"));
        if (!template.subtitle().isBlank()) {
            getChildren().add(text(template.subtitle(), 11, true, "#333333"));
        }
        for (String line : template.headerLines()) {
            getChildren().add(text(line, 10.5, false, "#333333"));
        }
        if (copyNumber > 0) {
            Label copy = text(template.copyMarker() + " #" + copyNumber, 11, true, "#000000");
            copy.setStyle(copy.getStyle() + " -fx-border-color: #000000; -fx-border-width: " + Math.max(1, 1.5 * scale)
                    + "; -fx-padding: " + 2 * scale + " " + 8 * scale + ";");
            getChildren().add(copy);
        }
        getChildren().add(rule(width - 2 * pad));

        if (template.slotBanner()) {
            getChildren().add(text(data.slotCode(), 46, true, "#000000"));
            String caption = data.location().isBlank() ? template.slotCaption() : data.location();
            getChildren().add(text(caption, 11.5, false, "#333333"));
            getChildren().add(rule(width - 2 * pad));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10 * scale);
        grid.setVgap(4 * scale);
        ColumnConstraints keys = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, values);
        grid.setMaxWidth(Double.MAX_VALUE);
        int r = 0;
        for (String[] line : data.lines(template, ZoneId.systemDefault())) {
            Label k = text(line[0], 11.5, false, "#333333");
            k.setMinWidth(Region.USE_PREF_SIZE);
            Label v = text(line[1], 12.5, true, "#000000");
            v.setWrapText(true);
            v.setAlignment(Pos.CENTER_LEFT);
            grid.addRow(r++, k, v);
        }
        getChildren().addAll(grid, rule(width - 2 * pad));

        for (String line : template.footerLines()) {
            Label f = text(line, 10.5, false, "#333333");
            f.setWrapText(true);
            f.setStyle(f.getStyle() + " -fx-text-alignment: center;");
            f.setMaxWidth(width - 2 * pad);
            f.setMinWidth(width - 2 * pad);
            f.setAlignment(Pos.CENTER);
            getChildren().add(f);
        }
    }

    private Label text(String value, double size, boolean bold, String colour) {
        Label l = new Label(value);
        l.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.1fpx; -fx-text-fill: %s;%s",
                size * scale, colour, bold ? " -fx-font-weight: bold;" : ""));
        return l;
    }

    private Region rule(double length) {
        Line line = new Line(0, 0, length, 0);
        line.getStrokeDashArray().addAll(4.0 * scale, 3.0 * scale);
        line.setStyle("-fx-stroke: #000000;");
        line.setStrokeWidth(Math.max(0.75, scale));
        VBox box = new VBox(line);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
