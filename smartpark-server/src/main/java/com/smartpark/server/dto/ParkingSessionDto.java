package com.smartpark.server.dto;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

/** A parking session; on entry this carries everything the printed receipt needs. */
@Schema(description = "A parking session (one ticket)")
public record ParkingSessionDto(
        String id,
        @Schema(example = "SP-20261113-000001") String ticketId,
        @Schema(example = "WPCAB1234", description = "Normalised: upper case, no spaces or hyphens") String vehicleNumber,
        @Schema(example = "CAR") String vehicleCategory,
        @Schema(example = "A-01") String parkingSlotCode,
        Instant entryTime,
        Instant exitTime,
        SessionStatus status,
        String entryOperator,
        String exitOperator,
        @Schema(description = "Length of stay in whole minutes; null while the session is ACTIVE")
        Long durationMinutes) {

    public static ParkingSessionDto from(ParkingSession s) {
        Long duration = s.getEntryTime() != null && s.getExitTime() != null
                ? Duration.between(s.getEntryTime(), s.getExitTime()).toMinutes() : null;
        return new ParkingSessionDto(s.getId(), s.getTicketId(), s.getVehicleNumber(), s.getVehicleCategory(),
                s.getParkingSlotCode(), s.getEntryTime(), s.getExitTime(), s.getStatus(),
                s.getEntryOperator(), s.getExitOperator(), duration);
    }
}
