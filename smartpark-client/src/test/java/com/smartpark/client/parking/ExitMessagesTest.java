package com.smartpark.client.parking;

import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.parking.ExitMessages.Kind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitMessagesTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Test
    void vehicleNeverParkedIsAWarning() {
        var m = ExitMessages.notParked("ABC123", null, COLOMBO);
        assertEquals(Kind.WARNING, m.kind());
        assertTrue(m.text().contains("ABC123 has no active parking session"));
    }

    @Test
    void alreadyCompletedSessionSaysWhenAndByWhom() {
        ParkingSession done = new ParkingSession("SP-20261113-000003", "WPCAB1234", "CAR", "A-03",
                Instant.parse("2026-11-13T07:53:00Z"), Instant.parse("2026-11-13T08:32:00Z"),
                "COMPLETED", "entry1", "exit1", 39L, null);

        var m = ExitMessages.notParked("WPCAB1234", done, COLOMBO);

        assertEquals(Kind.INFO, m.kind());
        assertEquals("WPCAB1234 is not parked now: ticket SP-20261113-000003 was already completed on 13 Nov, 14:02"
                + " by exit1 (slot A-03).", m.text());
    }

    @Test
    void networkAndServerErrorsMakeTheExitOutcomeUnknown() {
        assertTrue(ExitMessages.outcomeUnknown(ApiException.unreachable("connection refused")));
        assertTrue(ExitMessages.outcomeUnknown(new ApiException(500, "INTERNAL_ERROR", "boom", null)));
        assertTrue(ExitMessages.outcomeUnknown(new ApiException(503, "DATABASE_UNAVAILABLE", "db down", null)));
        assertFalse(ExitMessages.outcomeUnknown(new ApiException(404, "NO_ACTIVE_SESSION", "none", null)));
        assertFalse(ExitMessages.outcomeUnknown(new ApiException(403, "OPERATOR_NOT_AUTHORIZED", "no", null)));
    }

    @Test
    void searchFailuresAreExplained() {
        assertTrue(ExitMessages.searchFailed(ApiException.unreachable("timed out")).text().startsWith("Cannot reach"));
        assertTrue(ExitMessages.searchFailed(new ApiException(500, "INTERNAL_ERROR", "x", null)).text()
                .contains("INTERNAL_ERROR"));
        assertTrue(ExitMessages.searchFailed(new ApiException(400, "BAD_REQUEST", "Invalid vehicle number 'AB#1'", null))
                .text().startsWith("Invalid vehicle number"));
    }

    @Test
    void exitRejectedBecauseAlreadyCheckedOutChangesNothing() {
        var m = ExitMessages.exitRejected(new ApiException(404, "NO_ACTIVE_SESSION", "none", null), "ABC123");
        assertEquals(Kind.WARNING, m.kind());
        assertTrue(m.text().contains("already checked out") && m.text().contains("Nothing was changed"));
    }
}
