package com.smartpark.client.ui.component;

import com.smartpark.client.realtime.ConnectionState;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

/**
 * Coloured dot plus text showing the live connection to the central server:
 * green = connected, amber = connecting / reconnecting, red = offline or session refused.
 */
public final class ConnectionIndicator extends HBox {

    private final Circle dot = new Circle(5);
    private final Label label = new Label();
    private final Tooltip tooltip = new Tooltip();

    public ConnectionIndicator(ObservableValue<ConnectionState> state, ObservableValue<String> detail) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("connection-indicator");
        dot.getStyleClass().add("connection-dot");
        label.getStyleClass().add("connection-label");
        getChildren().addAll(dot, label);
        Tooltip.install(this, tooltip);

        state.addListener((obs, old, s) -> render(s));
        detail.addListener((obs, old, d) -> tooltip.setText(d));
        render(state.getValue());
        tooltip.setText(detail.getValue());
    }

    private void render(ConnectionState s) {
        getStyleClass().removeAll("state-online", "state-pending", "state-offline");
        switch (s) {
            case CONNECTED -> {
                getStyleClass().add("state-online");
                label.setText("Live");
            }
            case CONNECTING -> {
                getStyleClass().add("state-pending");
                label.setText("Connecting...");
            }
            case RECONNECTING -> {
                getStyleClass().add("state-pending");
                label.setText("Reconnecting...");
            }
            case UNAUTHORIZED -> {
                getStyleClass().add("state-offline");
                label.setText("Session ended");
            }
            default -> {
                getStyleClass().add("state-offline");
                label.setText("Offline");
            }
        }
    }
}
