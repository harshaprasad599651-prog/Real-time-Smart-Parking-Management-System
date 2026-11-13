package com.smartpark.server.controller;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.security.RequireRole;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.dto.CreateParkingSlotRequest;
import com.smartpark.server.dto.ParkingSlotDto;
import com.smartpark.server.dto.UpdateParkingSlotRequest;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.service.ParkingSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
@Tag(name = "Parking slots", description = "Slot map, availability and slot management")
public class ParkingSlotController {

    private final ParkingSlotService slotService;
    private final OperatorGuard operatorGuard;

    public ParkingSlotController(ParkingSlotService slotService, OperatorGuard operatorGuard) {
        this.slotService = slotService;
        this.operatorGuard = operatorGuard;
    }

    @GetMapping
    @Operation(summary = "List parking slots",
            description = "All slots ordered by floor, row and column. Every filter is optional and they can be combined.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.NotFound
    public List<ParkingSlotDto> list(
            @Parameter(description = "Only slots that accept this vehicle category", example = "CAR")
            @RequestParam(required = false) String category,
            @Parameter(description = "Only slots in this status")
            @RequestParam(required = false) SlotStatus status,
            @Parameter(description = "Only slots on this floor")
            @RequestParam(required = false) Integer floor) {
        return slotService.searchSlots(category, status, floor);
    }

    @GetMapping("/available")
    @Operation(summary = "List available slots",
            description = "AVAILABLE slots, optionally only those accepting a vehicle category. "
                    + "This is what the entry operator's map shows after choosing a category.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.NotFound
    public List<ParkingSlotDto> available(
            @Parameter(description = "Vehicle category", example = "MOTORCYCLE")
            @RequestParam(required = false) String category) {
        return slotService.searchSlots(category, SlotStatus.AVAILABLE, null);
    }

    @GetMapping("/{slotCode}")
    @Operation(summary = "Get one slot")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.NotFound
    public ParkingSlotDto get(@PathVariable String slotCode) {
        return slotService.getSlot(slotCode);
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping
    @Operation(summary = "Add a parking slot (ADMIN)",
            description = "If supportedVehicleCategories is omitted it is derived from the categories whose "
                    + "compatibleSlotTypes contain slotType. Slot code and map position must be unique.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to the new slot")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.Conflict
    public ResponseEntity<ParkingSlotDto> create(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @Valid @RequestBody CreateParkingSlotRequest request) {
        String admin = operatorGuard.requireAdmin(operator);
        ParkingSlotDto created = slotService.createSlot(request, admin);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{code}")
                .buildAndExpand(created.slotCode()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @RequireRole(UserRole.ADMIN)
    @PutMapping("/{slotCode}")
    @Operation(summary = "Update a parking slot (ADMIN)",
            description = "Replaces type, supported categories and position. Rejected with 409 while the slot is "
                    + "OCCUPIED, or if it changed concurrently (optimistic locking).")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ParkingSlotDto update(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @PathVariable String slotCode,
            @Valid @RequestBody UpdateParkingSlotRequest request) {
        String admin = operatorGuard.requireAdmin(operator);
        return slotService.updateSlot(slotCode, request, admin);
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/{slotCode}/disable")
    @Operation(summary = "Disable a slot (ADMIN)",
            description = "AVAILABLE -> DISABLED as one atomic conditional update. An OCCUPIED slot cannot be disabled (409).")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ParkingSlotDto disable(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @PathVariable String slotCode) {
        String admin = operatorGuard.requireAdmin(operator);
        return slotService.disableSlot(slotCode, admin);
    }

    @RequireRole(UserRole.ADMIN)
    @PostMapping("/{slotCode}/enable")
    @Operation(summary = "Re-enable a disabled slot (ADMIN)", description = "DISABLED -> AVAILABLE, atomically.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.Conflict
    public ParkingSlotDto enable(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @PathVariable String slotCode) {
        String admin = operatorGuard.requireAdmin(operator);
        return slotService.enableSlot(slotCode, admin);
    }
}
