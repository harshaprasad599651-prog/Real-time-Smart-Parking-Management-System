package com.smartpark.server.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical forms for identifiers typed by operators, so that {@code "wp cab-1234"}
 * and {@code "WPCAB1234"} are recognised as the same vehicle and the unique
 * indexes work on consistent values.
 */
public final class Normalizer {

    private static final Pattern VEHICLE_SEPARATORS = Pattern.compile("[\\s\\-]+");
    private static final Pattern VEHICLE_NUMBER = Pattern.compile("^[A-Z0-9]{2,12}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Normalizer() {
    }

    /** Upper-cases and removes spaces and hyphens: {@code "wp cab-1234" -> "WPCAB1234"}. */
    public static String vehicleNumber(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Vehicle number is required");
        }
        String normalized = VEHICLE_SEPARATORS.matcher(raw.trim()).replaceAll("").toUpperCase(Locale.ROOT);
        if (!VEHICLE_NUMBER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid vehicle number '" + raw + "': use 2-12 letters/digits (spaces and '-' are ignored)");
        }
        return normalized;
    }

    /** {@code " electric car "} -> {@code "ELECTRIC_CAR"}. */
    public static String categoryName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Category name is required");
        }
        return WHITESPACE.matcher(raw.trim()).replaceAll("_").toUpperCase(Locale.ROOT);
    }

    /** {@code " a-01 "} -> {@code "A-01"}. */
    public static String slotCode(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Slot code is required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /** Usernames are case-insensitive: stored and compared in lower case. */
    public static String username(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Username is required");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
