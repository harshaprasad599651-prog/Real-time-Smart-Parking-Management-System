package com.smartpark.client.service;

import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.dto.ApiDtos.Category;
import com.smartpark.client.net.dto.ApiDtos.EntryRequest;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.realtime.RealtimeEvent;
import tools.jackson.core.type.TypeReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side service for parking operations. Controllers call this, never the HTTP layer
 * directly, so endpoints, JSON types and event parsing live in one place.
 * <p>
 * All methods are asynchronous. The <b>server</b> takes every allocation decision: the client
 * only <i>requests</i> a slot, and the server's answer (201 or 409) is final.
 */
public final class ParkingService {

    private final ApiClient api;

    public ParkingService(ApiClient api) {
        this.api = api;
    }

    /** {@code GET /api/categories} */
    public CompletableFuture<List<Category>> categories() {
        return api.get("/api/categories", new TypeReference<List<Category>>() { });
    }

    /** {@code GET /api/slots}: every slot with its current status, for drawing the map. */
    public CompletableFuture<List<SlotInfo>> allSlots() {
        return api.get("/api/slots", new TypeReference<List<SlotInfo>>() { });
    }

    /** {@code GET /api/slots/available?category=}: the server's list of free slots for a vehicle category. */
    public CompletableFuture<List<SlotInfo>> availableSlots(String categoryName) {
        return api.get("/api/slots/available?category=" + URLEncoder.encode(categoryName, StandardCharsets.UTF_8),
                new TypeReference<List<SlotInfo>>() { });
    }

    /**
     * {@code POST /api/parking/entry}: asks the server to allocate the slot. Completes with the new
     * ACTIVE session (ticket ID, entry time), or fails with an {@code ApiException}, e.g.
     * SLOT_UNAVAILABLE if another terminal got the slot first.
     */
    public CompletableFuture<ParkingSession> registerEntry(EntryRequest request) {
        return api.post("/api/parking/entry", request, ParkingSession.class);
    }

    /** The slot carried by a SLOT_OCCUPIED / SLOT_RELEASED / PARKING_MAP_UPDATED event, if any. */
    public Optional<SlotInfo> slotOf(RealtimeEvent event) {
        if (event.payload() == null || !event.payload().hasNonNull("slot")) {
            return Optional.empty();
        }
        return Optional.of(api.json().treeToValue(event.payload().get("slot"), SlotInfo.class));
    }
}
