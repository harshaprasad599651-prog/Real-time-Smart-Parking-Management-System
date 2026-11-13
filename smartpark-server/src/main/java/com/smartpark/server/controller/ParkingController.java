package com.smartpark.server.controller;

import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.dto.ExitLookupResponse;
import com.smartpark.server.dto.PageResponse;
import com.smartpark.server.dto.ParkingEntryRequest;
import com.smartpark.server.dto.ParkingExitRequest;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.service.ParkingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/parking")
@Tag(name = "Parking", description = "Vehicle entry, vehicle exit and parking records")
public class ParkingController {

    private final ParkingSessionService sessionService;

    public ParkingController(ParkingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    // ------------------------------------------------------------------ entry

    @PostMapping("/entry")
    @Operation(summary = "Register a vehicle entry (ENTRY_OPERATOR or ADMIN)",
            description = """
                    Validates the vehicle number and category, rejects vehicles that are already parked, \
                    atomically claims the chosen slot, generates a unique ticket ID, stores the entry time \
                    (server clock) and creates an ACTIVE session. The response contains everything the \
                    receipt needs.

                    If two operators submit the same slot at the same time, exactly one gets 201 and the \
                    other gets 409 SLOT_UNAVAILABLE.""")
    @ApiResponse(responseCode = "201", description = "Vehicle parked; Location header points to the session")
    @ApiResponse(responseCode = "422", description = "Slot does not accept this category (SLOT_NOT_COMPATIBLE)")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ResponseEntity<ParkingSessionDto> entry(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @Valid @RequestBody ParkingEntryRequest request) {
        ParkingSessionDto session = sessionService.startParking(request.withOperator(operator.username()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/parking/sessions/{ticketId}").buildAndExpand(session.ticketId()).toUri();
        return ResponseEntity.created(location).body(session);
    }

    // ------------------------------------------------------------------- exit

    @GetMapping("/active/{vehicleNumber}")
    @Operation(summary = "Find the active session of a vehicle (exit gate search)",
            description = "Returns the vehicle's ACTIVE session, its assigned slot and the minutes parked so far. "
                    + "Spaces, hyphens and letter case in the vehicle number are ignored.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.NotFound
    public ExitLookupResponse findActive(
            @Parameter(example = "WPCAB1234") @PathVariable String vehicleNumber) {
        return sessionService.lookupForExit(vehicleNumber);
    }

    @PostMapping("/exit")
    @Operation(summary = "Confirm a vehicle departure (EXIT_OPERATOR or ADMIN)",
            description = "Atomically marks the ACTIVE session COMPLETED, stores the exit time and releases the slot. "
                    + "If two exit requests race, one gets 200 and the other gets 404 NO_ACTIVE_SESSION.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    public ParkingSessionDto exit(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @Valid @RequestBody ParkingExitRequest request) {
        return sessionService.completeParking(request.withOperator(operator.username()));
    }

    // -------------------------------------------------------------- records

    @GetMapping("/sessions/active")
    @Operation(summary = "List all currently parked vehicles", description = "ACTIVE sessions, newest entry first.")
    public List<ParkingSessionDto> activeSessions() {
        return sessionService.listActiveSessions();
    }

    @GetMapping("/sessions")
    @Operation(summary = "Search historical parking records",
            description = "Paged, newest entry first. All filters are optional; from/to filter on entry time "
                    + "(ISO-8601, e.g. 2026-11-13T00:00:00Z; from inclusive, to exclusive).")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    public PageResponse<ParkingSessionDto> searchSessions(
            @RequestParam(required = false) String vehicleNumber,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) String slotCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-200)") @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return sessionService.searchSessions(vehicleNumber, status, slotCode, from, to, page, size);
    }

    @GetMapping("/sessions/{ticketId}")
    @Operation(summary = "Get a session by ticket ID", description = "Used to reprint a receipt.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.NotFound
    public ParkingSessionDto byTicket(@Parameter(example = "SP-20261113-000001") @PathVariable String ticketId) {
        return sessionService.getByTicket(ticketId);
    }
}
