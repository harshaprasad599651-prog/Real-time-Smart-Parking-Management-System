package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.ReceiptInfo;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.print.ReceiptTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything printed on a parking receipt. It is only ever built from a <b>server</b> response:
 * the 201 answer of a successful entry, or the stored ticket fetched for a reprint. A rejected
 * allocation therefore can never produce a receipt. The entry time is the server's clock, so
 * receipts from every gate agree, whatever each PC's clock says.
 */
public record ReceiptData(String ticketId, String vehicleNumber, String category, String slotCode,
                          String slotType, int floor, String row, int column,
                          Instant entryTime, String operator) {

    /** From the entry response and the slot that was allocated. */
    public static ReceiptData of(ParkingSession session, SlotInfo slot) {
        return new ReceiptData(session.ticketId(), session.vehicleNumber(), session.vehicleCategory(),
                session.parkingSlotCode(), slot == null ? null : slot.slotType(),
                slot == null ? 0 : slot.floor(), slot == null ? null : slot.row(), slot == null ? 0 : slot.column(),
                session.entryTime(), session.entryOperator());
    }

    /** From the ticket stored on the server ({@code GET /api/parking/receipts/{ticketId}}). */
    public static ReceiptData of(ReceiptInfo r) {
        return new ReceiptData(r.ticketId(), r.vehicleNumber(), r.vehicleCategory(), r.parkingSlotCode(),
                r.slotType(), r.floor(), r.row(), r.column(), r.entryTime(), r.entryOperator());
    }

    /** "Floor 0 · Row B · Bay 5", or empty when the position is unknown. */
    public String location() {
        return row == null || row.isBlank() ? "" : "Floor " + floor + " · Row " + row + " · Bay " + column;
    }

    /** The value of one template field ({@link ReceiptTemplate#FIELD_KEYS}); empty if unknown. */
    public String value(String field, ReceiptTemplate t, ZoneId zone) {
        return switch (field) {
            case "ticket" -> ticketId;
            case "vehicle" -> vehicleNumber;
            case "category" -> pretty(category);
            case "slot" -> slotCode;
            case "location" -> location();
            case "slotType" -> pretty(slotType);
            case "date" -> entryTime == null ? "" : t.dateFormat().format(entryTime.atZone(zone));
            case "time" -> entryTime == null ? "" : t.timeFormat().format(entryTime.atZone(zone));
            case "operator" -> operator;
            default -> "";
        };
    }

    /** Label/value lines in the template's order; fields without a value are left out. */
    public List<String[]> lines(ReceiptTemplate t, ZoneId zone) {
        List<String[]> lines = new ArrayList<>();
        for (String field : t.fields()) {
            String v = value(field, t, zone);
            if (v != null && !v.isBlank()) {
                lines.add(new String[]{t.label(field), v});
            }
        }
        return lines;
    }

    private static String pretty(String enumName) {
        return enumName == null ? "" : enumName.replace('_', ' ');
    }
}
