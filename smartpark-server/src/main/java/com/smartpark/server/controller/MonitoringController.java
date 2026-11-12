package com.smartpark.server.controller;

import com.smartpark.server.dto.ActivityLogDto;
import com.smartpark.server.dto.ParkingStatisticsDto;
import com.smartpark.server.service.ActivityLogService;
import com.smartpark.server.service.ParkingStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Monitoring", description = "Dashboard statistics and operator activity")
public class MonitoringController {

    private final ParkingStatisticsService statisticsService;
    private final ActivityLogService activityLogService;

    public MonitoringController(ParkingStatisticsService statisticsService, ActivityLogService activityLogService) {
        this.statisticsService = statisticsService;
        this.activityLogService = activityLogService;
    }

    @GetMapping("/statistics")
    @Operation(summary = "Parking statistics",
            description = "Slot counts and occupancy, active sessions, today's entries/exits and average stay, "
                    + "plus per-category and per-floor breakdowns.")
    public ParkingStatisticsDto statistics() {
        return statisticsService.statistics();
    }

    @GetMapping("/activity")
    @Operation(summary = "Recent operator activity", description = "Newest first; optionally for one user.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    public List<ActivityLogDto> activity(
            @Parameter(example = "entry1") @RequestParam(required = false) String username,
            @Parameter(description = "Maximum entries (1-500)") @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return username == null || username.isBlank()
                ? activityLogService.recent(limit)
                : activityLogService.recentForUser(username.trim().toLowerCase(java.util.Locale.ROOT), limit);
    }
}
