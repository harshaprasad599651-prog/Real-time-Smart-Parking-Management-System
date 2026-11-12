package com.smartpark.server.exception;

/** The slot does not support the requested vehicle category. */
public class SlotNotCompatibleException extends SmartParkException {

    public SlotNotCompatibleException(String message) {
        super(message);
    }

    public SlotNotCompatibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
