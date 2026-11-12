/**
 * SmartPark JavaFX desktop client.
 * <p>
 * The same application runs on the Entry, Exit and Admin computers.
 */
module com.smartpark.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;   // Java HTTP Client + WebSocket (built into the JDK)

    // JavaFX launcher must be able to instantiate the Application subclass
    exports com.smartpark.client.app;

    // FXMLLoader uses reflection to create controllers and inject @FXML fields
    opens com.smartpark.client.ui to javafx.fxml;
}
