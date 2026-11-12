package com.smartpark.server.exception;

/** The requested state change is not allowed from the entity's current state. */
public class InvalidStateException extends SmartParkException {

    public InvalidStateException(String message) {
        super(message);
    }

    public InvalidStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
