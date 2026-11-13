package com.smartpark.client.ui.parking;

import com.smartpark.client.parking.ReceiptData;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

import java.time.ZoneId;

/**
 * The receipt as a JavaFX node, about 80 mm wide like a thermal receipt. The same node type
 * is shown on screen and sent to the printer, so the preview matches the print-out.
 * Styled inline (not via the app stylesheet) so it prints identically without a scene.
 */
final class ReceiptView extends VBox {

    static final double WIDTH = 300;

    ReceiptView(ReceiptData data) {
        super(10);
        setAlignment(Pos.TOP_CENTER);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        setStyle("-fx-background-color: white; -fx-padding: 20 22 18 22; -fx-font-family: 'Segoe UI', Arial;");

        Label brand = new Label("SmartPark");
        brand.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        Label sub = new Label("PARKING TICKET");
        sub.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        Label slot = new Label(data.slotCode());
        slot.setStyle("-fx-font-size: 46px; -fx-font-weight: bold; -fx-text-fill: #1d4ed8;");
        Label slotCaption = new Label(data.location().isBlank() ? "Your parking slot" : data.location());
        slotCaption.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        int r = 0;
        for (String[] line : data.lines(ZoneId.systemDefault())) {
            if (line[0].equals("Location")) {
                continue; // shown under the big slot code
            }
            Label k = new Label(line[0]);
            k.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            Label v = new Label(line[1]);
            v.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
            v.setWrapText(true);
            grid.addRow(r++, k, v);
        }

        Label footer = new Label("Keep this ticket. Present it at the exit gate.");
        footer.setWrapText(true);
        footer.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-text-alignment: center;");

        getChildren().addAll(brand, sub, rule(), slot, slotCaption, rule(), grid, rule(), footer);
    }

    private static Region rule() {
        Line line = new Line(0, 0, WIDTH - 44, 0);
        line.getStrokeDashArray().addAll(4.0, 4.0);
        line.setStyle("-fx-stroke: #94a3b8;");
        VBox box = new VBox(line);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
