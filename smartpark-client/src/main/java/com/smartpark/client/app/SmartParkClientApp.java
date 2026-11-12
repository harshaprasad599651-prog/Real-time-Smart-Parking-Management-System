package com.smartpark.client.app;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ServerApiClient;
import com.smartpark.client.ui.WelcomeController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * JavaFX entry point of the SmartPark desktop client.
 */
public class SmartParkClientApp extends Application {

    private static final String WELCOME_VIEW = "/fxml/welcome.fxml";
    private static final String STYLESHEET = "/css/smartpark.css";

    private ServerApiClient apiClient;

    @Override
    public void start(Stage stage) throws IOException {
        ClientConfig config = ClientConfig.load();
        apiClient = new ServerApiClient(config);

        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource(WELCOME_VIEW), "Missing " + WELCOME_VIEW));
        // Manual dependency injection: give the controller what it needs.
        loader.setControllerFactory(type -> {
            if (type == WelcomeController.class) {
                return new WelcomeController(config, apiClient);
            }
            throw new IllegalArgumentException("Unknown controller: " + type);
        });
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(STYLESHEET), "Missing " + STYLESHEET).toExternalForm());

        stage.setTitle("SmartPark - " + config.terminalName());
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (apiClient != null) {
            apiClient.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
