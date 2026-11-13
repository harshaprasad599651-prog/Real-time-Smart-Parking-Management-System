package com.smartpark.client.service;

import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.dto.ApiDtos.ActivityEntry;
import com.smartpark.client.net.dto.ApiDtos.ChangeRoleRequest;
import com.smartpark.client.net.dto.ApiDtos.CreateSlotRequest;
import com.smartpark.client.net.dto.ApiDtos.CreateUserRequest;
import com.smartpark.client.net.dto.ApiDtos.ParkingStatistics;
import com.smartpark.client.net.dto.ApiDtos.SessionPage;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.net.dto.ApiDtos.UpdateSlotRequest;
import com.smartpark.client.net.dto.ApiDtos.UserInfo;
import com.smartpark.client.session.UserRole;
import tools.jackson.core.type.TypeReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side service for the administrator screens. The server checks the ADMIN role on
 * every one of these endpoints; the client never relies on hiding buttons for security.
 */
public final class AdminService {

    private final ApiClient api;

    public AdminService(ApiClient api) {
        this.api = api;
    }

    // ------------------------------------------------------------ overview

    public CompletableFuture<ParkingStatistics> statistics() {
        return api.get("/api/statistics", ParkingStatistics.class);
    }

    // ------------------------------------------------------------- history

    /**
     * {@code GET /api/parking/sessions}: newest entry first. Every filter may be null.
     *
     * @param from inclusive, on entry time
     * @param to   exclusive, on entry time
     */
    public CompletableFuture<SessionPage> sessions(String vehicle, String status, Instant from, Instant to,
                                                   int page, int size) {
        StringBuilder q = new StringBuilder("/api/parking/sessions?page=").append(page).append("&size=").append(size);
        if (vehicle != null && !vehicle.isBlank()) {
            q.append("&vehicleNumber=").append(enc(vehicle.trim()));
        }
        if (status != null) {
            q.append("&status=").append(status);
        }
        if (from != null) {
            q.append("&from=").append(enc(from.toString()));
        }
        if (to != null) {
            q.append("&to=").append(enc(to.toString()));
        }
        return api.get(q.toString(), SessionPage.class);
    }

    // --------------------------------------------------------------- slots

    public CompletableFuture<SlotInfo> createSlot(CreateSlotRequest request) {
        return api.post("/api/slots", request, SlotInfo.class);
    }

    public CompletableFuture<SlotInfo> updateSlot(String slotCode, UpdateSlotRequest request) {
        return api.put("/api/slots/" + enc(slotCode), request, SlotInfo.class);
    }

    public CompletableFuture<SlotInfo> disableSlot(String slotCode) {
        return api.post("/api/slots/" + enc(slotCode) + "/disable", null, SlotInfo.class);
    }

    public CompletableFuture<SlotInfo> enableSlot(String slotCode) {
        return api.post("/api/slots/" + enc(slotCode) + "/enable", null, SlotInfo.class);
    }

    // --------------------------------------------------------------- users

    public CompletableFuture<List<UserInfo>> users() {
        return api.get("/api/users", new TypeReference<List<UserInfo>>() { });
    }

    public CompletableFuture<UserInfo> createUser(CreateUserRequest request) {
        return api.post("/api/users", request, UserInfo.class);
    }

    public CompletableFuture<UserInfo> changeRole(String username, UserRole role) {
        return api.put("/api/users/" + enc(username) + "/role", new ChangeRoleRequest(role), UserInfo.class);
    }

    public CompletableFuture<UserInfo> setActive(String username, boolean active) {
        return api.post("/api/users/" + enc(username) + (active ? "/activate" : "/deactivate"), null, UserInfo.class);
    }

    // ------------------------------------------------------------ activity

    /** {@code GET /api/activity}: newest first; role/action/username may be null. */
    public CompletableFuture<List<ActivityEntry>> activity(UserRole role, String action, String username, int limit) {
        StringBuilder q = new StringBuilder("/api/activity?limit=").append(limit);
        if (role != null) {
            q.append("&role=").append(role.name());
        }
        if (action != null) {
            q.append("&action=").append(action);
        }
        if (username != null && !username.isBlank()) {
            q.append("&username=").append(enc(username.trim()));
        }
        return api.get(q.toString(), new TypeReference<List<ActivityEntry>>() { });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
