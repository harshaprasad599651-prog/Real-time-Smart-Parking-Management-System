package com.smartpark.client.ui.common;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.ui.component.Icons;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Shown by the Navigator instead of a screen the signed-in role is not allowed to open. */
public class AccessDeniedController {

    private final AppContext context;

    @FXML private StackPane iconHolder;
    @FXML private Label detail;

    public AccessDeniedController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        iconHolder.getChildren().add(Icons.of(Icons.LOCK, 48, "denied-icon"));
        context.session().current().ifPresent(s -> detail.setText(
                "Signed in as " + s.username() + " (" + s.role().displayName() + "). "
                        + "Use the menu on the left to open the screens available to you."));
    }
}
