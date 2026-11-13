package com.smartpark.server.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-13T08:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(3, Duration.ofMinutes(15), Duration.ofMinutes(5), clock);

    @Test
    void locksAfterTheLimitAndUnlocksAfterTheLockout() {
        assertThat(limiter.recordFailure("entry1", "10.0.0.5")).isFalse();
        assertThat(limiter.recordFailure("entry1", "10.0.0.5")).isFalse();
        assertThat(limiter.lockedFor("entry1", "10.0.0.5")).isEmpty();
        assertThat(limiter.recordFailure("entry1", "10.0.0.5")).isTrue();

        assertThat(limiter.lockedFor("entry1", "10.0.0.9")).hasValue(Duration.ofMinutes(5)); // any computer
        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.lockedFor("entry1", null)).hasValue(Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThat(limiter.lockedFor("entry1", null)).isEmpty();
    }

    @Test
    void oldFailuresOutsideTheWindowDoNotCount() {
        limiter.recordFailure("exit1", null);
        limiter.recordFailure("exit1", null);
        clock.advance(Duration.ofMinutes(16));
        assertThat(limiter.recordFailure("exit1", null)).isFalse();
        assertThat(limiter.lockedFor("exit1", null)).isEmpty();
    }

    @Test
    void successClearsTheAccountCounter() {
        limiter.recordFailure("admin", null);
        limiter.recordFailure("admin", null);
        limiter.recordSuccess("admin");
        assertThat(limiter.recordFailure("admin", null)).isFalse();
    }

    @Test
    void oneComputerTryingManyUsernamesIsLockedToo() {
        int limit = 3 * LoginAttemptLimiter.ADDRESS_FACTOR;
        for (int i = 0; i < limit - 1; i++) {
            limiter.recordFailure("user" + i, "10.0.0.66");
        }
        assertThat(limiter.lockedFor("brand-new-user", "10.0.0.66")).isEmpty();
        limiter.recordFailure("one-more", "10.0.0.66");
        assertThat(limiter.lockedFor("brand-new-user", "10.0.0.66")).isPresent();
        assertThat(limiter.lockedFor("brand-new-user", "10.0.0.67")).isEmpty();      // other computers fine
    }
}
