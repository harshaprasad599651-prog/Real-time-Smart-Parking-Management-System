package com.smartpark.client.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formats. Server times are UTC instants; they are shown in this PC's time zone. */
public final class Formats {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter DATE_TIME_SECONDS =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm:ss", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private Formats() {
    }

    public static String time(Instant instant) {
        return instant == null ? "–" : TIME.format(instant);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "–" : DATE_TIME.format(instant);
    }

    /** {@code "13 Nov, 14:45:12"} */
    public static String dateTimeSeconds(Instant instant) {
        return instant == null ? "–" : DATE_TIME_SECONDS.format(instant);
    }

    public static String percent(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    public static String minutes(Double value) {
        if (value == null) {
            return "–";
        }
        long total = Math.round(value);
        return total < 60 ? total + " min" : (total / 60) + " h " + (total % 60) + " min";
    }
}
