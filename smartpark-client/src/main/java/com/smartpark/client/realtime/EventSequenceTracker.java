package com.smartpark.client.realtime;

import java.util.LinkedHashSet;
import java.util.SequencedSet;

/**
 * Decides whether an incoming event is new, a duplicate, or follows a gap.
 * <p>
 * The server numbers broadcast events 1, 2, 3 ... per server run ({@code serverInstanceId}).
 * After a reconnect the server may replay events the client already processed (for example,
 * the connection dropped just after an event arrived). Anything with
 * {@code sequence <= lastSequence}, or an {@code eventId} seen recently, is a duplicate
 * and is dropped, so the UI never applies the same change twice.
 */
public final class EventSequenceTracker {

    public enum Decision {
        /** New, next in order: process it. */
        DELIVER,
        /** New, but some events before it are missing: process it and reload state. */
        DELIVER_AFTER_GAP,
        /** Already processed: ignore it. */
        DUPLICATE
    }

    private static final int REMEMBERED_IDS = 2_000;

    private String serverInstanceId;
    private long lastSequence;
    private final SequencedSet<String> recentIds = new LinkedHashSet<>();

    /**
     * Applies a CONNECTION_ACK.
     *
     * @return true if this is a fresh start (first connection, server restarted, or resync
     *         required), meaning the caller must reload its state
     */
    public synchronized boolean onAck(ConnectionAck ack) {
        boolean fresh = serverInstanceId == null
                || !serverInstanceId.equals(ack.serverInstanceId())
                || ack.resyncRequired();
        if (fresh) {
            serverInstanceId = ack.serverInstanceId();
            lastSequence = ack.currentSequence(); // continue from "now"; state comes from REST
            recentIds.clear();
        }
        // otherwise: keep lastSequence; the replayed events follow and continue from it
        return fresh;
    }

    public synchronized Decision accept(RealtimeEvent event) {
        if (event.isControl()) {
            return Decision.DELIVER;
        }
        if (event.sequence() <= lastSequence || recentIds.contains(event.eventId())) {
            return Decision.DUPLICATE;
        }
        Decision decision = event.sequence() == lastSequence + 1 ? Decision.DELIVER : Decision.DELIVER_AFTER_GAP;
        lastSequence = event.sequence();
        recentIds.add(event.eventId());
        if (recentIds.size() > REMEMBERED_IDS) {
            recentIds.removeFirst();
        }
        return decision;
    }

    public synchronized long lastSequence() {
        return lastSequence;
    }

    public synchronized String serverInstanceId() {
        return serverInstanceId;
    }
}
