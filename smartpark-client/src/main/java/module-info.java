/**
 * SmartPark JavaFX desktop client.
 * <p>
 * The same application runs on the Entry, Exit and Admin computers.
 */
module com.smartpark.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;            // Java HTTP Client + WebSocket (built into the JDK)
    requires tools.jackson.databind;   // JSON
    requires java.logging;

    // JavaFX launcher must be able to instantiate the Application subclass
    exports com.smartpark.client.app;
    // Real-time client API, used by the UI and the command-line test client
    exports com.smartpark.client.realtime;
    exports com.smartpark.client.tools;

    // FXMLLoader uses reflection to create controllers and inject @FXML fields
    opens com.smartpark.client.ui to javafx.fxml;
    // Jackson creates the event records by reflection
    opens com.smartpark.client.realtime to tools.jackson.databind;
}
