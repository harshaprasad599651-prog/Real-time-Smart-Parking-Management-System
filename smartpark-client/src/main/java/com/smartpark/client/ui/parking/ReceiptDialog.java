package com.smartpark.client.ui.parking;

import com.smartpark.client.parking.ReceiptData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shows the <b>prepared</b> parking receipt after a successful allocation, built from the
 * server's response (ticket ID and server entry time).
 * <p>
 * Physical printing is a later stage: {@link ReceiptView} is a self-contained node, so it can be
 * handed to {@code javafx.print.PrinterJob} unchanged when printing is added.
 */
final class ReceiptDialog {

    private ReceiptDialog() {
    }

    /** @param onClosed run when the operator closes the receipt (e.g. to focus the form for the next vehicle) */
    static void show(Window owner, ReceiptData data, String stylesheet, Runnable onClosed) {
        Stage stage = new Stage();
        stage.setOnHidden(e -> onClosed.run());
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Parking receipt - " + data.ticketId());

        Label ready = new Label("Receipt prepared for ticket " + data.ticketId());
        ready.getStyleClass().add("success-banner");
        ready.setMaxWidth(Double.MAX_VALUE);
        Label note = new Label("Printing will be available in a later version.");
        note.getStyleClass().add("muted");

        Button done = new Button("Done");
        done.getStyleClass().add("primary-button");
        done.setDefaultButton(true);
        done.setCancelButton(true);
        done.setOnAction(e -> stage.close());

        StackPane paper = new StackPane(new ReceiptView(data));
        paper.getStyleClass().add("receipt-paper");

        HBox buttons = new HBox(10, note, done);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(note, javafx.scene.layout.Priority.ALWAYS);
        note.setMaxWidth(Double.MAX_VALUE);
        VBox root = new VBox(16, ready, paper, buttons);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("receipt-dialog");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        done.requestFocus();
    }
}
