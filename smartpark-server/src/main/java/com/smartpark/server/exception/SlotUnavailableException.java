package com.smartpark.server.exception;

/** The slot cannot be allocated now: it is occupied, disabled, or was just taken by another operator. */
public class SlotUnavailableException extends SmartParkException {

    public SlotUnavailableException(String message) {
        super(message);
    }

    public SlotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
