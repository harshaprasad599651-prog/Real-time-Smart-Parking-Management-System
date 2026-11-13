package com.smartpark.client.ui.login;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.Health;
import com.smartpark.client.ui.component.Icons;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/**
 * Login screen. Credentials go to {@code POST /api/auth/login} through the {@code SessionManager}.
 * On success the session listener (wired in the application class) opens the main layout;
 * this controller only deals with input, progress and error messages.
 */
public class LoginController {

    private final AppContext context;

    @FXML private StackPane brandIcon;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label errorLabel;
    @FXML private Label infoLabel;
    @FXML private Label serverLabel;
    @FXML private Label serverStatus;
    @FXML private Label terminalLabel;

    public LoginController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        brandIcon.getChildren().add(Icons.of(Icons.CAR, 44, "brand-icon"));
        serverLabel.setText(context.config().serverHost() + ":" + context.config().serverPort());
        terminalLabel.setText(context.config().terminalName());
        showError(null);
        showMessage(null);
        progress.setVisible(false);
        signInButton.setDefaultButton(true);   // Enter submits
        checkServer();
        Platform.runLater(usernameField::requestFocus);
    }

    /** Informational banner, e.g. "You have signed out." or "Your session has expired." */
    public void showMessage(String message) {
        infoLabel.setText(message);
        infoLabel.setVisible(message != null);
        infoLabel.setManaged(message != null);
    }

    @FXML
    private void onSignIn() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter your username and password.");
            (username.isEmpty() ? usernameField : passwordField).requestFocus();
            return;
        }
        setBusy(true);
        showError(null);
        showMessage(null);
        context.session().login(username, password).whenComplete((session, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                ApiException e = ApiClient.unwrap(error);
                showError(switch (e.code()) {
                    case "INVALID_CREDENTIALS" -> "Incorrect username or password.";
                    case ApiException.SERVER_UNREACHABLE -> "Cannot reach the SmartPark server at "
                            + context.config().serverHost() + ":" + context.config().serverPort() + ".";
                    default -> e.userMessage();
                });
                passwordField.clear();
                passwordField.requestFocus();
                checkServer();
            }
            // success: SessionManager fires SIGNED_IN and the navigator shows the dashboard
        }));
    }

    private void checkServer() {
        serverStatus.setText("Checking...");
        serverStatus.getStyleClass().setAll("label", "server-status", "state-pending");
        context.api().get("/api/health", Health.class).whenComplete((health, error) -> Platform.runLater(() -> {
            boolean up = error == null && "UP".equals(health.status());
            serverStatus.setText(up ? "Online" : "Unreachable");
            serverStatus.getStyleClass().setAll("label", "server-status", up ? "state-online" : "state-offline");
        }));
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        signInButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        signInButton.setText(busy ? "Signing in..." : "Sign in");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(message != null);
        errorLabel.setManaged(message != null);
    }
}
