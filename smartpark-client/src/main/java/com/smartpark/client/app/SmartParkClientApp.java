package com.smartpark.client.app;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.ui.navigation.Navigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * JavaFX entry point of the SmartPark desktop client (same program on all three PCs).
 * <p>
 * Start-up wiring:
 * <pre>
 *   SessionManager.SIGNED_IN   -> open the WebSocket, show the main layout with the role's home screen
 *   SessionManager.SIGNED_OUT  -> close the WebSocket, show login ("You have signed out")
 *   SessionManager.EXPIRED     -> close the WebSocket, show login ("Your session has expired")
 *   WebSocket refused (401)    -> treated like EXPIRED
 * </pre>
 */
public class SmartParkClientApp extends Application {

    private static final String STYLESHEET = "/css/smartpark.css";

    private AppContext context;

    @Override
    public void start(Stage stage) {
        context = new AppContext(ClientConfig.load());

        // Size the window for the current screen: 90 % of it, but within sensible limits.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.max(1024, Math.min(1440, screen.getWidth() * 0.9));
        double height = Math.max(640, Math.min(900, screen.getHeight() * 0.9));

        Scene scene = new Scene(new StackPane(), width, height);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(STYLESHEET)).toExternalForm());
        Navigator navigator = new Navigator(stage, context, scene);

        context.session().addListener((event, session) -> Platform.runLater(() -> {
            switch (event) {
                case SIGNED_IN -> {
                    context.realtime().start(session);
                    navigator.showMainLayout(session);
                }
                case SIGNED_OUT -> {
                    context.realtime().stop();
                    navigator.showLogin("You have signed out.");
                }
                case EXPIRED -> {
                    context.realtime().stop();
                    navigator.showLogin("Your session has expired or was ended by the server. Please sign in again.");
                }
            }
        }));
        context.realtime().setOnUnauthorized(() -> context.session().invalidate());

        navigator.showLogin(null);
        stage.setMinWidth(1024);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close(); // signs out on the server and closes the WebSocket
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
