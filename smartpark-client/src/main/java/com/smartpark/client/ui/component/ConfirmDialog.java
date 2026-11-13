package com.smartpark.client.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;

/**
 * Reusable modal "please confirm" dialog in the SmartPark style: a heading, a short message,
 * a table of label/value rows and Cancel / confirm buttons.
 * <pre>
 *   boolean ok = ConfirmDialog.ask(window, "Confirm parking allocation", "Check the details ...",
 *           List.of(new String[]{"Vehicle", "WPCAB1234"}, new String[]{"Slot", "A-03"}), "Allocate slot");
 * </pre>
 * Blocks (nested event loop) until the operator decides; Esc or closing the window means Cancel.
 */
public final class ConfirmDialog {

    private static final String STYLESHEET = "/css/smartpark.css";

    private ConfirmDialog() {
    }

    /** Read-only details window with a single Close button (non-blocking). */
    public static void info(Window owner, String title, List<String[]> rows) {
        Stage stage = build(owner, title, null, rows, null, new boolean[1]);
        stage.show();
    }

    public static boolean ask(Window owner, String title, String message, List<String[]> rows, String confirmText) {
        boolean[] confirmed = {false};
        Stage stage = build(owner, title, message, rows, confirmText, confirmed);
        stage.showAndWait();
        return confirmed[0];
    }

    /** @param confirmText null for an information window (Close button only) */
    private static Stage build(Window owner, String title, String message, List<String[]> rows, String confirmText,
                               boolean[] confirmed) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setResizable(false);

        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        Label text = new Label(message);
        text.getStyleClass().add("muted");
        text.setWrapText(true);
        text.setVisible(message != null);
        text.setManaged(message != null);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-details");
        grid.setHgap(18);
        grid.setVgap(8);
        grid.getColumnConstraints().addAll(new ColumnConstraints(110), new ColumnConstraints());
        int r = 0;
        for (String[] row : rows) {
            Label k = new Label(row[0]);
            k.getStyleClass().add("muted");
            Label v = new Label(row[1]);
            v.getStyleClass().add(r == 0 ? "dialog-value-strong" : "detail-value");
            v.setWrapText(true);
            v.setMaxWidth(300);
            grid.addRow(r++, k, v);
        }

        Button cancel = new Button(confirmText == null ? "Close" : "Cancel");
        cancel.getStyleClass().add(confirmText == null ? "primary-button" : "secondary-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        HBox buttons = new HBox(10, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button ok = cancel;
        if (confirmText != null) {
            ok = new Button(confirmText);
            ok.getStyleClass().add("primary-button");
            ok.setDefaultButton(true);
            ok.setOnAction(e -> {
                confirmed[0] = true;
                stage.close();
            });
            buttons.getChildren().add(ok);
        } else {
            cancel.setDefaultButton(true);
        }

        VBox root = new VBox(14, heading, text, grid, buttons);
        root.setPadding(new Insets(22, 24, 20, 24));
        root.setPrefWidth(460);
        root.getStyleClass().add("dialog-root");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(ConfirmDialog.class.getResource(STYLESHEET)).toExternalForm());
        stage.setScene(scene);
        ok.requestFocus();
        return stage;
    }
}
