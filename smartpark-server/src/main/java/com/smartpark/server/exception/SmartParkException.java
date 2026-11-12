package com.smartpark.server.exception;

/**
 * Base class of all expected business errors. A later REST layer will map each
 * subclass to an HTTP status (404, 409, 403 ...).
 */
public abstract class SmartParkException extends RuntimeException {

    protected SmartParkException(String message) {
        super(message);
    }

    protected SmartParkException(String message, Throwable cause) {
        super(message, cause);
    }
}
