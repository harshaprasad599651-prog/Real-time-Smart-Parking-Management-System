package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.ReceiptInfo;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.print.PaperSize;
import com.smartpark.client.print.ReceiptTemplate;
import com.smartpark.client.util.VehicleNumbers;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptAndValidationTest {

    private static final ReceiptTemplate TEMPLATE = ReceiptTemplate.load();

    @Test
    void receiptContainsTicketVehicleSlotServerEntryTimeAndOperator() {
        ParkingSession session = new ParkingSession("SP-20261113-000007", "WPCAB1234", "ELECTRIC_CAR", "B-05",
                Instant.parse("2026-11-13T03:15:30Z"), null, "ACTIVE", "entry1", null, null, null);
        SlotInfo slot = new SlotInfo("B-05", "EV_CHARGING", List.of("ELECTRIC_CAR"), "OCCUPIED", 0, "B", 5, "SP-20261113-000007");

        ReceiptData r = ReceiptData.of(session, slot);
        List<String[]> lines = r.lines(TEMPLATE, ZoneId.of("Asia/Colombo")); // UTC+05:30

        assertEquals(7, lines.size());
        assertArrayEquals(new String[]{"Ticket ID", "SP-20261113-000007"}, lines.get(0));
        assertArrayEquals(new String[]{"Vehicle No.", "WPCAB1234"}, lines.get(1));
        assertArrayEquals(new String[]{"Category", "ELECTRIC CAR"}, lines.get(2));
        assertArrayEquals(new String[]{"Slot", "B-05"}, lines.get(3));
        assertArrayEquals(new String[]{"Entry date", "13 Nov 2026"}, lines.get(4));
        assertArrayEquals(new String[]{"Entry time", "08:45:30"}, lines.get(5));
        assertArrayEquals(new String[]{"Operator", "entry1"}, lines.get(6));
        assertEquals("Floor 0 · Row B · Bay 5", r.location());
        assertEquals("SmartPark", TEMPLATE.title());
    }

    @Test
    void reprintUsesTheTicketStoredOnTheServer() {
        ReceiptInfo stored = new ReceiptInfo("SP-20261113-000003", "NB5678", "CAR", "A-06", "STANDARD", 1, "A", 6,
                Instant.parse("2026-11-13T07:55:00Z"), "entry1", "COMPLETED", 2, null, "entry1");
        ReceiptData r = ReceiptData.of(stored);
        assertEquals("A-06", r.slotCode());
        assertEquals("Floor 1 · Row A · Bay 6", r.location());
        assertEquals("entry1", r.value("operator", TEMPLATE, ZoneId.of("UTC")));
        assertEquals("07:55:00", r.value("time", TEMPLATE, ZoneId.of("UTC")));
    }

    @Test
    void templateFieldsLabelsAndFormatsAreCustomisable() {
        Properties p = new Properties();
        p.setProperty("receipt.title", "City Car Park");
        p.setProperty("receipt.header.lines", "Level 2 | Colombo 03");
        p.setProperty("receipt.fields", "vehicle, slot, location, unknownField, date");
        p.setProperty("receipt.label.vehicle", "Plate");
        p.setProperty("receipt.date.pattern", "yyyy-MM-dd");
        p.setProperty("receipt.time.pattern", "not a [valid pattern");   // falls back to the default
        ReceiptTemplate t = ReceiptTemplate.from(p);

        ReceiptData r = new ReceiptData("T-1", "ABC123", "VAN", "B-01", "LARGE", 0, "B", 1,
                Instant.parse("2026-11-13T10:00:00Z"), "entry1");
        List<String[]> lines = r.lines(t, ZoneId.of("UTC"));

        assertEquals("City Car Park", t.title());
        assertEquals(List.of("Level 2", "Colombo 03"), t.headerLines());
        assertEquals(4, lines.size());                                       // unknown field ignored
        assertArrayEquals(new String[]{"Plate", "ABC123"}, lines.get(0));
        assertArrayEquals(new String[]{"Location", "Floor 0 · Row B · Bay 1"}, lines.get(2));
        assertArrayEquals(new String[]{"Entry date", "2026-11-13"}, lines.get(3));
        assertEquals("10:00:00", r.value("time", t, ZoneId.of("UTC")));
    }

    @Test
    void paperSizeIsDetectedFromThePrintersPaperWidth() {
        assertEquals(PaperSize.THERMAL_58MM, PaperSize.forPaperWidth(mmToPt(58)));
        assertEquals(PaperSize.THERMAL_80MM, PaperSize.forPaperWidth(mmToPt(80)));
        assertEquals(PaperSize.THERMAL_80MM, PaperSize.forPaperWidth(mmToPt(72)));
        assertEquals(PaperSize.STANDARD, PaperSize.forPaperWidth(mmToPt(210)));    // A4
        assertEquals(PaperSize.STANDARD, PaperSize.forPaperWidth(612));            // Letter (8.5 in)
        assertEquals(PaperSize.AUTO, PaperSize.parse(""));
        assertEquals(PaperSize.THERMAL_80MM, PaperSize.parse("thermal_80mm"));
        assertEquals(PaperSize.AUTO, PaperSize.parse("banana"));
        assertTrue(PaperSize.THERMAL_58MM.contentWidth() < PaperSize.THERMAL_80MM.contentWidth());
        assertTrue(PaperSize.THERMAL_80MM.contentWidth() <= mmToPt(72) + 0.01);
    }

    private static double mmToPt(double mm) {
        return mm / 25.4 * 72;
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
