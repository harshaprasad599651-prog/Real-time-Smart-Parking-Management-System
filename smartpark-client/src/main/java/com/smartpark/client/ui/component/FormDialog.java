package com.smartpark.client.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Objects;

/**
 * Reusable modal form window: title, form content, error banner, Cancel / OK.
 * <p>
 * Unlike {@link ConfirmDialog} it does not close on OK: the caller sends the request and either
 * {@link #close()}s on success or shows the server's error with {@link #showError(String)},
 * so the admin can correct the input without retyping everything.
 */
public final class FormDialog {

    private final Stage stage = new Stage();
    private final Label error = new Label();
    private final Button ok;
    private final Button cancel = new Button("Cancel");

    public FormDialog(Window owner, String title, Node form, String okText) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.setResizable(false);

        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        error.getStyleClass().add("error-banner");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        hideError();

        ok = new Button(okText);
        ok.getStyleClass().add("primary-button");
        ok.setDefaultButton(true);
        cancel.getStyleClass().add("secondary-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        HBox buttons = new HBox(10, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(14, heading, form, error, buttons);
        root.setPadding(new Insets(22, 24, 20, 24));
        root.setPrefWidth(480);
        root.getStyleClass().add("dialog-root");
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(FormDialog.class.getResource("/css/smartpark.css")).toExternalForm());
        stage.setScene(scene);
    }

    /** What happens when the admin presses OK (validate, then send the request). */
    public FormDialog onSubmit(Runnable action) {
        ok.setOnAction(e -> action.run());
        return this;
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }

    public void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
        stage.sizeToScene();
    }

    public void hideError() {
        error.setVisible(false);
        error.setManaged(false);
    }

    /** Disables the buttons while a request is in flight. */
    public void setBusy(boolean busy) {
        ok.setDisable(busy);
        cancel.setDisable(busy);
    }
}
