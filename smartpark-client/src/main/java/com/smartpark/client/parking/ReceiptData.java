package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Everything printed on a parking receipt. The entry time is the <b>server's</b> time from the
 * entry response, so receipts from every gate agree, whatever each PC's clock says.
 */
public record ReceiptData(String ticketId, String vehicleNumber, String category, String slotCode,
                          String location, Instant entryTime, String operator, String terminal) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static ReceiptData of(ParkingSession session, SlotInfo slot, String terminal) {
        String location = slot == null ? "" : "Floor " + slot.floor() + " · Row " + slot.row() + " · Bay " + slot.column();
        return new ReceiptData(session.ticketId(), session.vehicleNumber(),
                session.vehicleCategory().replace('_', ' '), session.parkingSlotCode(), location,
                session.entryTime(), session.entryOperator(), terminal);
    }

    public String entryDate(ZoneId zone) {
        return DATE.format(entryTime.atZone(zone));
    }

    public String entryClock(ZoneId zone) {
        return TIME.format(entryTime.atZone(zone));
    }

    /** Label/value lines shown on the receipt, in order. */
    public List<String[]> lines(ZoneId zone) {
        return List.of(
                new String[]{"Ticket", ticketId},
                new String[]{"Vehicle", vehicleNumber},
                new String[]{"Category", category},
                new String[]{"Slot", slotCode},
                new String[]{"Location", location},
                new String[]{"Entry date", entryDate(zone)},
                new String[]{"Entry time", entryClock(zone)},
                new String[]{"Operator", operator + " @ " + terminal});
    }
}
