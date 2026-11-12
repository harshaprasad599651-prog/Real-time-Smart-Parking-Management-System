package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** What the exit operator sees after searching a vehicle number, before confirming departure. */
@Schema(description = "Active session of a vehicle together with its assigned slot")
public record ExitLookupResponse(
        ParkingSessionDto session,
        ParkingSlotDto slot,
        @Schema(description = "Minutes since entry, using the server clock") long minutesParked) {
}
