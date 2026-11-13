package com.smartpark.server.exception;

/** The request has no valid credentials (missing, expired or revoked token). Mapped to 401. */
public class UnauthenticatedException extends SmartParkException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
