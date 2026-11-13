package com.smartpark.server.controller;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.security.RequireRole;
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

    @RequireRole(UserRole.ADMIN)
    @GetMapping("/activity")
    @Operation(summary = "Operator activity (ADMIN)",
            description = "Newest first. Optional filters: one user, all users with a role (e.g. ENTRY_OPERATOR), "
                    + "one action (e.g. VEHICLE_EXIT). New entries are also pushed live to admin terminals "
                    + "as ACTIVITY_RECORDED WebSocket messages.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    public List<ActivityLogDto> activity(
            @Parameter(example = "entry1") @RequestParam(required = false) String username,
            @Parameter(description = "Only users with this role") @RequestParam(required = false) UserRole role,
            @Parameter(description = "Only this action") @RequestParam(required = false) ActivityAction action,
            @Parameter(description = "Maximum entries (1-500)") @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return activityLogService.search(username, role, action, limit);
    }
}
