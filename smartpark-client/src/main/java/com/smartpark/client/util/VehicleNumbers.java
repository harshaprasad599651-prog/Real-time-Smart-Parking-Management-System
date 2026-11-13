package com.smartpark.client.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Client-side copy of the server's vehicle-number rules, so the operator gets instant feedback
 * before anything is sent. The server checks again; this only saves a round trip.
 */
public final class VehicleNumbers {

    /** Same pattern as the server's request validation: letters/digits, spaces or '-' inside. */
    private static final Pattern INPUT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 -]{0,18}[A-Za-z0-9]$");
    private static final Pattern NORMALIZED = Pattern.compile("^[A-Z0-9]{2,12}$");

    private VehicleNumbers() {
    }

    /** {@code " wp cab-1234 "} -> {@code "WPCAB1234"} (the form the server stores). */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    /** @return an error message for the operator, or empty if the number is acceptable */
    public static Optional<String> validate(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.of("Enter the vehicle registration number.");
        }
        if (!INPUT.matcher(trimmed).matches() || !NORMALIZED.matcher(normalize(trimmed)).matches()) {
            return Optional.of("Use 2-12 letters and digits, e.g. WP CAB-1234.");
        }
        return Optional.empty();
    }
}
