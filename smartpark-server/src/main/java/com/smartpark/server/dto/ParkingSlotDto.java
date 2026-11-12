package com.smartpark.server.dto;

import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.SlotType;

import java.util.Set;

public record ParkingSlotDto(String id, String slotCode, SlotType slotType, Set<String> supportedVehicleCategories,
                             SlotStatus status, int floor, String row, int column, String currentTicketId) {

    public static ParkingSlotDto from(ParkingSlot s) {
        return new ParkingSlotDto(s.getId(), s.getSlotCode(), s.getSlotType(),
                Set.copyOf(s.getSupportedVehicleCategories()), s.getStatus(),
                s.getFloor(), s.getRow(), s.getColumn(), s.getCurrentTicketId());
    }
}
