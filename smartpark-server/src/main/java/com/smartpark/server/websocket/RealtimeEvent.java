package com.smartpark.server.websocket;

import java.time.Instant;

/**
 * Envelope of every message sent to clients, serialised as JSON:
 * <pre>
 * {
 *   "eventId": "3f9c...",              unique id of this event
 *   "sequence": 42,                    1, 2, 3 ... per server run (0 for control messages)
 *   "serverInstanceId": "a1b2c3d4",    changes when the server restarts
 *   "type": "SLOT_OCCUPIED",
 *   "timestamp": "2026-11-13T08:15:30.123Z",
 *   "actor": "entry1",                 operator who caused it (or SYSTEM)
 *   "terminal": "ENTRY-GATE-1",        terminal of that operator, if connected
 *   "payload": { ... }                 type-specific data
 * }
 * </pre>
 * Clients use {@code serverInstanceId + sequence} to drop duplicates and to detect missed events.
 */
public record RealtimeEvent(
        String eventId,
        long sequence,
        String serverInstanceId,
        RealtimeEventType type,
        Instant timestamp,
        String actor,
        String terminal,
        Object payload) {
}
