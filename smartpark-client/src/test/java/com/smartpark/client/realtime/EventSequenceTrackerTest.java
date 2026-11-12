package com.smartpark.client.realtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.smartpark.client.realtime.EventSequenceTracker.Decision.DELIVER;
import static com.smartpark.client.realtime.EventSequenceTracker.Decision.DELIVER_AFTER_GAP;
import static com.smartpark.client.realtime.EventSequenceTracker.Decision.DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSequenceTrackerTest {

    private final EventSequenceTracker tracker = new EventSequenceTracker();

    @Test
    void firstConnectionStartsFromServersCurrentSequence() {
        assertTrue(tracker.onAck(ack("srv1", 10, 0, false)));

        assertEquals(DUPLICATE, tracker.accept(event("e10", 10, "srv1")));
        assertEquals(DELIVER, tracker.accept(event("e11", 11, "srv1")));
        assertEquals(DELIVER, tracker.accept(event("e12", 12, "srv1")));
        assertEquals(12, tracker.lastSequence());
    }

    @Test
    void replayedEventsAlreadyProcessedAreDropped() {
        tracker.onAck(ack("srv1", 0, 0, false));
        tracker.accept(event("e1", 1, "srv1"));
        tracker.accept(event("e2", 2, "srv1"));

        // reconnect to the same server: replay may overlap with what we already have
        assertFalse(tracker.onAck(ack("srv1", 4, 3, false)));
        assertEquals(DUPLICATE, tracker.accept(event("e2", 2, "srv1")));
        assertEquals(DELIVER, tracker.accept(event("e3", 3, "srv1")));
        assertEquals(DELIVER, tracker.accept(event("e4", 4, "srv1")));
        assertEquals(DUPLICATE, tracker.accept(event("e4", 4, "srv1")));
    }

    @Test
    void gapIsReported() {
        tracker.onAck(ack("srv1", 0, 0, false));
        tracker.accept(event("e1", 1, "srv1"));

        assertEquals(DELIVER_AFTER_GAP, tracker.accept(event("e5", 5, "srv1")));
        assertEquals(DELIVER, tracker.accept(event("e6", 6, "srv1")));
    }

    @Test
    void serverRestartResetsSequence() {
        tracker.onAck(ack("srv1", 0, 0, false));
        tracker.accept(event("a", 1, "srv1"));
        tracker.accept(event("b", 2, "srv1"));

        assertTrue(tracker.onAck(ack("srv2", 0, 0, true)));
        assertEquals(DELIVER, tracker.accept(event("c", 1, "srv2")));
    }

    @Test
    void controlMessagesAreAlwaysDelivered() {
        tracker.onAck(ack("srv1", 5, 0, false));
        assertEquals(DELIVER, tracker.accept(event("pong", 0, "srv1")));
    }

    private static ConnectionAck ack(String instance, long seq, int replayed, boolean resync) {
        return new ConnectionAck("s", instance, seq, replayed, resync, resync ? "SERVER_RESTARTED" : null, 15);
    }

    private static RealtimeEvent event(String id, long seq, String instance) {
        return new RealtimeEvent(id, seq, instance, "SLOT_OCCUPIED", Instant.now(), "entry1", null, null);
    }
}
