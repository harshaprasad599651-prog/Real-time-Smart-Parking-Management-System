package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HourlyTrafficTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo"); // UTC+05:30

    @Test
    void countsTodaysEntriesAndExitsPerLocalHour() {
        Instant now = Instant.parse("2026-11-13T09:30:00Z"); // 15:00 local
        List<ParkingSession> sessions = List.of(
                session("2026-11-13T03:10:00Z", "2026-11-13T04:40:00Z"),   // in 08:40, out 10:10
                session("2026-11-13T03:20:00Z", null),                     // in 08:50, still parked
                session("2026-11-12T17:00:00Z", "2026-11-13T05:00:00Z"));  // in yesterday 22:30, out 10:30

        HourlyTraffic t = HourlyTraffic.today(sessions, COLOMBO, now);

        assertEquals(16, t.byHour().size());                 // 00..15
        assertArrayEquals(new int[]{2, 0}, t.byHour().get(8));
        assertArrayEquals(new int[]{0, 2}, t.byHour().get(10));
        assertEquals(2, t.totalEntries());                    // yesterday's entry not counted
        assertEquals(2, t.totalExits());
    }

    private static ParkingSession session(String in, String out) {
        return new ParkingSession("T", "V", "CAR", "A-01", Instant.parse(in), out == null ? null : Instant.parse(out),
                out == null ? "ACTIVE" : "COMPLETED", "entry1", out == null ? null : "exit1", null, null);
    }
}
