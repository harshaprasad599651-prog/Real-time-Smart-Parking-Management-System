package com.smartpark.server.exception;

/** A referenced user, category, slot or session does not exist. */
public class ResourceNotFoundException extends SmartParkException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
