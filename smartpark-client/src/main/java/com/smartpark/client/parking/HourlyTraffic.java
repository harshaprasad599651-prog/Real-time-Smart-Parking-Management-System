package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.ParkingSession;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Entries and exits per hour of the current day (local time), for the admin's bar chart.
 * Plain Java, unit-tested.
 *
 * @param byHour hour of day (0..current hour) -> {entries, exits}
 */
public record HourlyTraffic(Map<Integer, int[]> byHour) {

    public static HourlyTraffic today(Collection<ParkingSession> sessions, ZoneId zone, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        int currentHour = now.atZone(zone).getHour();
        Map<Integer, int[]> hours = new TreeMap<>();
        for (int h = 0; h <= currentHour; h++) {
            hours.put(h, new int[2]);
        }
        for (ParkingSession s : sessions) {
            count(hours, s.entryTime(), today, zone, 0);
            count(hours, s.exitTime(), today, zone, 1);
        }
        return new HourlyTraffic(hours);
    }

    private static void count(Map<Integer, int[]> hours, Instant t, LocalDate today, ZoneId zone, int index) {
        if (t != null && LocalDate.ofInstant(t, zone).equals(today)) {
            int[] counts = hours.get(t.atZone(zone).getHour());
            if (counts != null) {
                counts[index]++;
            }
        }
    }

    public int totalEntries() {
        return byHour.values().stream().mapToInt(c -> c[0]).sum();
    }

    public int totalExits() {
        return byHour.values().stream().mapToInt(c -> c[1]).sum();
    }
}
