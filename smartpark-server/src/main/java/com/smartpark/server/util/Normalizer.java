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
    /** Control and invisible formatting characters (line breaks, tabs, zero-width, bidi overrides ...). */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]");
    private static final Pattern LABEL_UNSAFE = Pattern.compile("[^A-Za-z0-9 ._@-]");
    private static final Pattern CLIENT_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private Normalizer() {
    }

    /**
     * Free text from users (notes, descriptions, printer names): control characters become spaces,
     * runs of whitespace collapse to one space, the result is trimmed and cut to {@code maxLength}.
     * Blank input gives {@code null}. This keeps line breaks and invisible characters out of
     * receipts, logs and the activity feed (no forged log lines).
     */
    public static String freeText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String cleaned = WHITESPACE.matcher(CONTROL.matcher(raw).replaceAll(" ")).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
    }

    /**
     * Short identifiers chosen by a client, such as terminal names: free text limited to letters,
     * digits, space and {@code . _ @ -} (anything else becomes {@code _}), or {@code fallback}.
     */
    public static String label(String raw, int maxLength, String fallback) {
        String text = freeText(raw, maxLength);
        return text == null ? fallback : LABEL_UNSAFE.matcher(text).replaceAll("_");
    }

    /** A client-generated connection id (UUID-like); anything else gives {@code null}. */
    public static String clientId(String raw) {
        return raw != null && CLIENT_ID.matcher(raw).matches() ? raw : null;
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
