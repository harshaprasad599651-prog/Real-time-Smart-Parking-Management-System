package com.smartpark.server.exception;

/**
 * Another operator changed the same record at the same moment and the operation could
 * not be completed after retrying. The client should refresh and try again.
 */
public class ConcurrentUpdateException extends SmartParkException {

    public ConcurrentUpdateException(String message) {
        super(message);
    }

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
