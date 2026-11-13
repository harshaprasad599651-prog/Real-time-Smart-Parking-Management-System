package com.smartpark.client.net.dto;

import com.smartpark.client.session.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * Client-side copies of the server's JSON messages. Only the fields the client uses are
 * declared; Jackson 3 ignores unknown properties by default, so the server can add
 * fields without breaking older clients.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record LoginRequest(String username, String password, String terminal) {
        @Override
        public String toString() {
            return "LoginRequest[" + username + "@" + terminal + "]";
        }
    }

    public record UserInfo(String id, String username, UserRole role, boolean active, Instant createdAt) {
    }

    public record LoginResponse(String token, String tokenType, Instant expiresAt, UserInfo user) {
    }

    public record Health(String status, String service, Instant serverTime) {
    }

    public record CategoryStatistics(String categoryName, long compatibleSlots, long availableSlots, long parkedVehicles) {
    }

    public record FloorStatistics(int floor, long totalSlots, long availableSlots, long occupiedSlots, long disabledSlots) {
    }

    public record ParkingStatistics(Instant generatedAt, long totalSlots, long availableSlots, long occupiedSlots,
                                    long disabledSlots, double occupancyRatePercent, long activeSessions,
                                    long entriesToday, long exitsToday, Double averageStayMinutesToday,
                                    List<CategoryStatistics> categories, List<FloorStatistics> floors) {
    }

    public record ConnectionInfo(String sessionId, String clientId, String operator, UserRole role, String terminal,
                                 String remoteAddress, Instant connectedAt, Instant lastSeenAt) {
    }

    public record RealtimeStatus(String serverInstanceId, long currentSequence, int connectedClients,
                                 List<ConnectionInfo> clients) {
    }

    public record ParkingSession(String ticketId, String vehicleNumber, String vehicleCategory, String parkingSlotCode,
                                 Instant entryTime, Instant exitTime, String status, String entryOperator,
                                 String exitOperator, Long durationMinutes, String notes) {
    }

    /** A parking slot ({@code GET /api/slots}, and the {@code slot} in real-time slot events). */
    public record SlotInfo(String slotCode, String slotType, List<String> supportedVehicleCategories, String status,
                           int floor, String row, int column, String currentTicketId) {

        public boolean isAvailable() {
            return "AVAILABLE".equals(status);
        }

        public boolean accepts(String category) {
            return category != null && supportedVehicleCategories != null && supportedVehicleCategories.contains(category);
        }
    }

    public record Category(String categoryName, String description, List<String> compatibleSlotTypes) {
        /** "ELECTRIC_CAR" -> "Electric car". */
        public String displayName() {
            String s = categoryName.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    /** Body of {@code POST /api/parking/entry}; {@code notes} is optional (max 200 characters). */
    public record EntryRequest(String vehicleNumber, String categoryName, String slotCode, String notes) {
    }

    public record ExitRequest(String vehicleNumber) {
    }

    // ------------------------------------------------------------ admin

    /** One activity-log entry ({@code GET /api/activity}, and the payload of ACTIVITY_RECORDED). */
    public record ActivityEntry(String id, String username, String action, String description, Instant timestamp) {
    }

    public record CreateUserRequest(String username, String password, UserRole role) {
        @Override
        public String toString() {
            return "CreateUserRequest[" + username + ", " + role + "]"; // never print the password
        }
    }

    public record ChangeRoleRequest(UserRole role) {
    }

    /** Body of {@code POST /api/slots}; empty categories = derived from the slot type. */
    public record CreateSlotRequest(String slotCode, String slotType, List<String> supportedVehicleCategories,
                                    int floor, String row, int column) {
    }

    /** Body of {@code PUT /api/slots/{code}}. */
    public record UpdateSlotRequest(String slotType, List<String> supportedVehicleCategories,
                                    int floor, String row, int column) {
    }

    /** A page of parking sessions ({@code GET /api/parking/sessions?...}). */
    public record SessionPage(List<ParkingSession> content, int page, int size, long totalElements, int totalPages) {
    }

    /** {@code GET /api/parking/active/{vehicle}}: what the exit operator reviews before confirming. */
    public record ExitLookup(ParkingSession session, SlotInfo slot, long minutesParked) {
    }
}
