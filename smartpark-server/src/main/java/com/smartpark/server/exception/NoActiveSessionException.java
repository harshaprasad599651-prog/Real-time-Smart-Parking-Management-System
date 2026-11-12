package com.smartpark.server.exception;

/** No ACTIVE parking session exists for the given vehicle. */
public class NoActiveSessionException extends SmartParkException {

    public NoActiveSessionException(String message) {
        super(message);
    }

    public NoActiveSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
