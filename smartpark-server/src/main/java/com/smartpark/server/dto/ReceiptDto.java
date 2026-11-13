package com.smartpark.server.dto;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.TicketReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Everything a parking receipt shows, read from the stored ticket. The client lays it out with
 * its own template; the server only provides the data (entry time from the server clock).
 */
@Schema(description = "Stored ticket information used to print or reprint a parking receipt")
public record ReceiptDto(
        @Schema(example = "SP-20261113-000001") String ticketId,
        @Schema(example = "WPCAB1234") String vehicleNumber,
        @Schema(example = "CAR") String vehicleCategory,
        @Schema(example = "A-01") String parkingSlotCode,
        @Schema(example = "STANDARD") String slotType,
        int floor,
        String row,
        int column,
        @Schema(description = "Server time of the entry") Instant entryTime,
        @Schema(example = "entry1") String entryOperator,
        @Schema(description = "ACTIVE while parked, COMPLETED after exit") SessionStatus sessionStatus,
        @Schema(description = "Times printed so far; when > 0 the next print is a reprint (copy)") int printCount,
        Instant lastPrintedAt,
        String lastPrintedBy) {

    /**
     * @param currentSlot used only for sessions created before ticket information was stored
     *                    (may be null)
     */
    public static ReceiptDto from(ParkingSession s, ParkingSlot currentSlot) {
        TicketReceipt r = s.getReceipt();
        String slotType = r != null && r.getSlotType() != null ? r.getSlotType().name()
                : currentSlot != null && currentSlot.getSlotType() != null ? currentSlot.getSlotType().name() : null;
        int floor = r != null ? r.getFloor() : currentSlot != null ? currentSlot.getFloor() : 0;
        String row = r != null ? r.getRow() : currentSlot != null ? currentSlot.getRow() : null;
        int column = r != null ? r.getColumn() : currentSlot != null ? currentSlot.getColumn() : 0;
        return new ReceiptDto(s.getTicketId(), s.getVehicleNumber(), s.getVehicleCategory(), s.getParkingSlotCode(),
                slotType, floor, row, column, s.getEntryTime(), s.getEntryOperator(), s.getStatus(),
                r == null ? 0 : r.getPrintCount(),
                r == null ? null : r.getLastPrintedAt(),
                r == null ? null : r.getLastPrintedBy());
    }
}
