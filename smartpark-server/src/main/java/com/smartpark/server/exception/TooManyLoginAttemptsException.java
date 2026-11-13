package com.smartpark.server.exception;

import java.time.Duration;

/** Too many failed sign-ins for this account or computer; mapped to 429 with Retry-After. */
public class TooManyLoginAttemptsException extends SmartParkException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed sign-in attempts. Try again in " + Math.max(1, (retryAfter.toSeconds() + 59) / 60)
                + " minute(s).");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
