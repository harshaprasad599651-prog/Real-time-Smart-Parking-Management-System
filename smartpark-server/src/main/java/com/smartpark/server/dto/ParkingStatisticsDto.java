package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Snapshot of the car park for the admin dashboard. "Today" uses the server's time zone. */
@Schema(description = "Occupancy and traffic statistics")
public record ParkingStatisticsDto(
        Instant generatedAt,
        long totalSlots,
        long availableSlots,
        long occupiedSlots,
        long disabledSlots,
        @Schema(description = "occupied / (total - disabled) * 100, rounded to 1 decimal")
        double occupancyRatePercent,
        long activeSessions,
        long entriesToday,
        long exitsToday,
        @Schema(description = "Average stay of sessions completed today, in minutes; null if none")
        Double averageStayMinutesToday,
        List<CategoryStatistics> categories,
        List<FloorStatistics> floors) {

    @Schema(description = "Per vehicle category: slots it may use and vehicles currently parked")
    public record CategoryStatistics(String categoryName, long compatibleSlots, long availableSlots, long parkedVehicles) {
    }

    @Schema(description = "Per floor slot counts")
    public record FloorStatistics(int floor, long totalSlots, long availableSlots, long occupiedSlots, long disabledSlots) {
    }
}
