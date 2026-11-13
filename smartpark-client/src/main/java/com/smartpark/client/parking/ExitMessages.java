package com.smartpark.client.parking;

import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * What the exit operator is told in each situation. Plain Java (no JavaFX), so every case is unit-tested.
 * <ul>
 *     <li>invalid vehicle number: checked locally before any request;</li>
 *     <li>no active session: the vehicle is not parked here;</li>
 *     <li>already completed: it was parked, but has already been checked out (when and by whom);</li>
 *     <li>network failure: nothing reached the server, or the outcome of an exit is unknown;</li>
 *     <li>server error: 5xx, nothing has been changed.</li>
 * </ul>
 */
public final class ExitMessages {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH);

    /** The kind of banner to show. */
    public enum Kind { ERROR, WARNING, INFO }

    public record Message(Kind kind, String text) {
    }

    private ExitMessages() {
    }

    /** Vehicle has no ACTIVE session; {@code lastCompleted} is its most recent finished session, if any. */
    public static Message notParked(String vehicle, ParkingSession lastCompleted, ZoneId zone) {
        if (lastCompleted == null) {
            return new Message(Kind.WARNING, vehicle + " has no active parking session. Check the number on the ticket.");
        }
        return new Message(Kind.INFO, vehicle + " is not parked now: ticket " + lastCompleted.ticketId()
                + " was already completed" + at(lastCompleted.exitTime(), zone)
                + (lastCompleted.exitOperator() == null ? "" : " by " + lastCompleted.exitOperator())
                + " (slot " + lastCompleted.parkingSlotCode() + ").");
    }

    /** A search ({@code GET /api/parking/active/...}) failed for a reason other than "not parked". */
    public static Message searchFailed(ApiException e) {
        return switch (category(e)) {
            case NETWORK -> new Message(Kind.ERROR, "Cannot reach the SmartPark server. Check the network connection and try again.");
            case SERVER -> new Message(Kind.ERROR, "The server could not complete the search (" + e.code()
                    + "). Please try again in a moment.");
            case INVALID -> new Message(Kind.ERROR, "Invalid vehicle number: " + e.userMessage());
            case FORBIDDEN -> new Message(Kind.ERROR, e.getMessage());
            case OTHER -> new Message(Kind.ERROR, e.userMessage());
        };
    }

    /** The exit request was rejected by the server (a definite "no"; nothing was changed). */
    public static Message exitRejected(ApiException e, String vehicle) {
        if ("NO_ACTIVE_SESSION".equals(e.code())) {
            return new Message(Kind.WARNING, vehicle + " was already checked out (probably at another exit terminal). "
                    + "Nothing was changed.");
        }
        return switch (category(e)) {
            case INVALID -> new Message(Kind.ERROR, "Invalid request: " + e.userMessage());
            case FORBIDDEN -> new Message(Kind.ERROR, e.getMessage());
            default -> new Message(Kind.ERROR, e.userMessage());
        };
    }

    /**
     * True when we cannot know whether the server completed the exit: the connection failed after
     * the request may have been sent, or the server answered 5xx. The client must then ask the
     * server before telling the operator anything.
     */
    public static boolean outcomeUnknown(ApiException e) {
        return category(e) == Category.NETWORK || category(e) == Category.SERVER;
    }

    public static Message verifying() {
        return new Message(Kind.WARNING, "No answer from the server. Checking whether the exit was recorded...");
    }

    public static Message verifiedNotRecorded() {
        return new Message(Kind.WARNING, "The exit was NOT recorded; the vehicle is still parked. Please confirm again.");
    }

    public static Message verificationFailed() {
        return new Message(Kind.ERROR, "The server is still unreachable, so it is unknown whether the exit was recorded. "
                + "Do not let the vehicle leave yet; search again when the connection is back.");
    }

    // ---------------------------------------------------------------- helpers

    enum Category { NETWORK, SERVER, INVALID, FORBIDDEN, OTHER }

    static Category category(ApiException e) {
        if (ApiException.SERVER_UNREACHABLE.equals(e.code()) || e.status() == 0) {
            return Category.NETWORK;
        }
        if (e.status() >= 500) {
            return Category.SERVER;
        }
        if (e.status() == 400) {
            return Category.INVALID;
        }
        if (e.status() == 403) {
            return Category.FORBIDDEN;
        }
        return Category.OTHER;
    }

    private static String at(Instant t, ZoneId zone) {
        return t == null ? "" : " on " + WHEN.format(t.atZone(zone));
    }
}
