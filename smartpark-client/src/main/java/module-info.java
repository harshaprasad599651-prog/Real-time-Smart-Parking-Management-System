/**
 * SmartPark JavaFX desktop client.
 * <p>
 * The same application runs on the Entry, Exit and Admin computers; the signed-in role
 * decides which screens are available.
 */
module com.smartpark.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;            // Java HTTP Client + WebSocket (built into the JDK)
    requires tools.jackson.databind;   // JSON
    requires java.logging;

    // JavaFX launcher must be able to instantiate the Application subclass
    exports com.smartpark.client.app;
    // Reusable client APIs (used by the UI and the command-line test client)
    exports com.smartpark.client.realtime;
    exports com.smartpark.client.tools;

    // FXMLLoader creates controllers and injects @FXML fields by reflection
    opens com.smartpark.client.ui.login to javafx.fxml;
    opens com.smartpark.client.ui.shell to javafx.fxml;
    opens com.smartpark.client.ui.dashboard to javafx.fxml;
    opens com.smartpark.client.ui.connections to javafx.fxml;
    opens com.smartpark.client.ui.parking to javafx.fxml;
    opens com.smartpark.client.ui.admin to javafx.fxml;
    opens com.smartpark.client.ui.common to javafx.fxml;

    // Jackson creates the JSON records by reflection
    opens com.smartpark.client.realtime to tools.jackson.databind;
    opens com.smartpark.client.net.dto to tools.jackson.databind;
    opens com.smartpark.client.session to tools.jackson.databind;
}
