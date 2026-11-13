package com.smartpark.server.exception;

/**
 * Wrong username or password, or the account is inactive. Mapped to 401.
 * The message deliberately does not say which, so it doesn't help someone guessing usernames.
 */
public class InvalidCredentialsException extends SmartParkException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
