package com.smartpark.server.websocket;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ParkingSlotDto;

import java.time.Instant;
import java.util.List;

/** Payload records carried inside {@link RealtimeEvent#payload()}. */
public final class RealtimePayloads {

    private RealtimePayloads() {
    }

    /**
     * Payload of SLOT_OCCUPIED and SLOT_RELEASED.
     *
     * @param reason VEHICLE_ENTRY, VEHICLE_EXIT or RECONCILIATION
     */
    public record SlotChange(ParkingSlotDto slot, String reason, String ticketId, String vehicleNumber) {
    }

    /**
     * Payload of PARKING_MAP_UPDATED.
     *
     * @param reason       SLOT_CREATED, SLOT_UPDATED, SLOT_DISABLED, SLOT_ENABLED, CATEGORY_CREATED, CATEGORY_UPDATED
     * @param slot         the changed slot; {@code null} means several slots may have changed, so reload the map
     * @param categoryName the changed category, if any
     */
    public record MapUpdate(String reason, ParkingSlotDto slot, String categoryName) {
    }

    /** One connected client, as shown on the admin's connection monitor. */
    public record ConnectionInfo(String sessionId, String clientId, String operator, UserRole role, String terminal,
                                 String remoteAddress, Instant connectedAt, Instant lastSeenAt) {
    }

    /** Payload of OPERATOR_CONNECTED and OPERATOR_DISCONNECTED. */
    public record OperatorPresence(ConnectionInfo connection, int connectedClients, String closeReason) {
    }

    /**
     * Payload of CONNECTION_ACK.
     *
     * @param currentSequence  sequence number of the latest broadcast event
     * @param replayedEvents   how many missed events follow this message (after a reconnect)
     * @param resyncRequired   true if missed events could not be replayed: reload state through the REST API
     * @param heartbeatSeconds how often the client should send a ping
     */
    public record ConnectionAck(String sessionId, String serverInstanceId, long currentSequence, int replayedEvents,
                                boolean resyncRequired, String resyncReason, int heartbeatSeconds,
                                List<ConnectionInfo> connectedClients) {
    }

    /** Response of {@code GET /api/realtime/status}. */
    public record RealtimeStatus(String serverInstanceId, long currentSequence, int connectedClients,
                                 List<ConnectionInfo> clients) {
    }
}
