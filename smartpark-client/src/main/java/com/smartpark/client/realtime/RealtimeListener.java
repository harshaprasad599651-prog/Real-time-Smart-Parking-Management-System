package com.smartpark.client.realtime;

/**
 * Callbacks from {@link RealtimeClient}. All callbacks of one client run on a single
 * background thread, in order. A JavaFX UI must hop to the FX thread with
 * {@code Platform.runLater(...)} before touching controls.
 */
public interface RealtimeListener {

    /** Connection status changed (for the status indicator). */
    default void onStateChanged(ConnectionState state, String detail) {
    }

    /** A (re)connection was acknowledged by the server. */
    default void onConnected(ConnectionAck ack) {
    }

    /**
     * Local state may be out of date and must be reloaded through the REST API.
     * Called on the first connection ({@code INITIAL_CONNECTION}), after a server restart
     * ({@code SERVER_RESTARTED}), after a long outage ({@code TOO_MANY_MISSED_EVENTS}) or if a
     * gap in the sequence numbers is detected ({@code SEQUENCE_GAP}).
     */
    default void onResyncRequired(String reason) {
    }

    /** A new broadcast event. Each event is delivered at most once, in server order. */
    default void onEvent(RealtimeEvent event) {
    }
}
