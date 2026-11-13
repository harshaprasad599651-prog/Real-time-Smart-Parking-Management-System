package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.util.VehicleNumbers;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptAndValidationTest {

    @Test
    void receiptContainsTicketVehicleSlotAndServerEntryTime() {
        ParkingSession session = new ParkingSession("SP-20261113-000007", "WPCAB1234", "ELECTRIC_CAR", "B-05",
                Instant.parse("2026-11-13T03:15:30Z"), null, "ACTIVE", "entry1", null, null, null);
        SlotInfo slot = new SlotInfo("B-05", "EV_CHARGING", List.of("ELECTRIC_CAR"), "OCCUPIED", 0, "B", 5, "SP-20261113-000007");

        ReceiptData r = ReceiptData.of(session, slot, "ENTRY-GATE-1");
        List<String[]> lines = r.lines(ZoneId.of("Asia/Colombo")); // UTC+05:30

        assertArrayEquals(new String[]{"Ticket", "SP-20261113-000007"}, lines.get(0));
        assertArrayEquals(new String[]{"Vehicle", "WPCAB1234"}, lines.get(1));
        assertArrayEquals(new String[]{"Category", "ELECTRIC CAR"}, lines.get(2));
        assertArrayEquals(new String[]{"Slot", "B-05"}, lines.get(3));
        assertArrayEquals(new String[]{"Location", "Floor 0 · Row B · Bay 5"}, lines.get(4));
        assertArrayEquals(new String[]{"Entry date", "13 Nov 2026"}, lines.get(5));
        assertArrayEquals(new String[]{"Entry time", "08:45:30"}, lines.get(6));
        assertArrayEquals(new String[]{"Operator", "entry1 @ ENTRY-GATE-1"}, lines.get(7));
    }

    @Test
    void vehicleNumbersAreValidatedLikeTheServer() {
        assertTrue(VehicleNumbers.validate("WP CAB-1234").isEmpty());
        assertTrue(VehicleNumbers.validate("ab12").isEmpty());
        assertTrue(VehicleNumbers.validate("").isPresent());
        assertTrue(VehicleNumbers.validate("A").isPresent());
        assertTrue(VehicleNumbers.validate("AB#12").isPresent());
        assertTrue(VehicleNumbers.validate("-AB12").isPresent());
        assertEquals("WPCAB1234", VehicleNumbers.normalize(" wp cab-1234 "));
    }
}
