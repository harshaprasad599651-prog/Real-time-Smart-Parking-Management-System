package com.smartpark.client.ui;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ServerApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;

/**
 * Controller of the welcome screen. Shows this terminal's identity and lets the
 * operator test connectivity with the central server.
 */
public class WelcomeController {

    private final ClientConfig config;
    private final ServerApiClient apiClient;

    @FXML private Label terminalLabel;
    @FXML private Label serverLabel;
    @FXML private Label statusLabel;
    @FXML private Button checkButton;

    public WelcomeController(ClientConfig config, ServerApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
    }

    @FXML
    private void initialize() {
        terminalLabel.setText("Terminal: " + config.terminalName());
        serverLabel.setText("Server: " + apiClient.baseUri());
        showStatus("Not checked yet", "status-neutral");
    }

    @FXML
    private void onCheckConnection() {
        checkButton.setDisable(true);
        showStatus("Connecting...", "status-neutral");

        apiClient.checkHealth().whenComplete((body, error) -> Platform.runLater(() -> {
            checkButton.setDisable(false);
            if (error == null) {
                showStatus("Connected - server is UP", "status-ok");
            } else {
                showStatus("Server unreachable: " + describe(error), "status-error");
            }
        }));
    }

    private void showStatus(String text, String styleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("status-neutral", "status-ok", "status-error");
        statusLabel.getStyleClass().add(styleClass);
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        if (cause instanceof HttpConnectTimeoutException || cause instanceof HttpTimeoutException) {
            return "timed out";
        }
        if (cause instanceof ConnectException) {
            return "connection refused";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
