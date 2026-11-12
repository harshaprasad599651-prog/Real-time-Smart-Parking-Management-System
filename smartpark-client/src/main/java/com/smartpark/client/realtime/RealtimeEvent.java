package com.smartpark.client.realtime;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * A message received from the server (same envelope as the server's {@code RealtimeEvent}).
 *
 * @param sequence 1, 2, 3 ... for broadcast events; 0 for control messages (CONNECTION_ACK, PONG)
 * @param type     VEHICLE_ENTERED, VEHICLE_EXITED, SLOT_OCCUPIED, SLOT_RELEASED, PARKING_MAP_UPDATED,
 *                 OPERATOR_CONNECTED, OPERATOR_DISCONNECTED, CONNECTION_ACK or PONG
 * @param payload  type-specific JSON
 */
public record RealtimeEvent(String eventId, long sequence, String serverInstanceId, String type,
                            Instant timestamp, String actor, String terminal, JsonNode payload) {

    public boolean isControl() {
        return sequence == 0;
    }

    /** Convenience: text of a payload field such as {@code "vehicleNumber"} or {@code "slot.slotCode"}. */
    public String payloadText(String dottedPath) {
        JsonNode node = payload;
        for (String part : dottedPath.split("\\.")) {
            if (node == null) {
                return null;
            }
            node = node.get(part);
        }
        return node == null || node.isNull() ? null : node.asString();
    }
}
