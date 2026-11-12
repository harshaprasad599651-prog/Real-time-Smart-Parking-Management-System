package com.smartpark.server.websocket;

/**
 * Types of message sent over the real-time WebSocket.
 * <p>
 * <b>Broadcast events</b> are delivered to every connected client and carry an increasing
 * {@code sequence} number. <b>Control messages</b> go to one client only and have
 * {@code sequence = 0}.
 */
public enum RealtimeEventType {

    // ---- broadcast events (sequenced, replayable) ----
    /** A vehicle was parked. Payload: the new ACTIVE session. */
    VEHICLE_ENTERED(true),
    /** A vehicle left. Payload: the COMPLETED session (exit time, duration). */
    VEHICLE_EXITED(true),
    /** A slot became OCCUPIED. Payload: slot + ticket + vehicle. */
    SLOT_OCCUPIED(true),
    /** A slot became AVAILABLE again. Payload: slot + reason. */
    SLOT_RELEASED(true),
    /** Slots or categories were added, edited, disabled or enabled. Payload: reason + slot (null = reload all). */
    PARKING_MAP_UPDATED(true),
    /** A client connected. Payload: connection details + number of connected clients. */
    OPERATOR_CONNECTED(true),
    /** A client disconnected. Payload: connection details + close reason. */
    OPERATOR_DISCONNECTED(true),

    // ---- control messages (to one client, not sequenced) ----
    /** First message after connecting: server instance, current sequence, replay/resync information. */
    CONNECTION_ACK(false),
    /** Reply to an application-level {@code PING} text message. */
    PONG(false);

    private final boolean broadcast;

    RealtimeEventType(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public boolean isBroadcast() {
        return broadcast;
    }
}
