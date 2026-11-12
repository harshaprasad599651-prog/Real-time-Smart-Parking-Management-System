package com.smartpark.server.dto;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.SessionStatus;

import java.time.Instant;

/** A parking session; on entry this carries everything the printed receipt needs. */
public record ParkingSessionDto(String id, String ticketId, String vehicleNumber, String vehicleCategory,
                                String parkingSlotCode, Instant entryTime, Instant exitTime, SessionStatus status,
                                String entryOperator, String exitOperator) {

    public static ParkingSessionDto from(ParkingSession s) {
        return new ParkingSessionDto(s.getId(), s.getTicketId(), s.getVehicleNumber(), s.getVehicleCategory(),
                s.getParkingSlotCode(), s.getEntryTime(), s.getExitTime(), s.getStatus(),
                s.getEntryOperator(), s.getExitOperator());
    }
}
