package com.smartpark.server.exception;

/** A unique value (username, category name, slot code, slot position) is already taken. */
public class DuplicateResourceException extends SmartParkException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
