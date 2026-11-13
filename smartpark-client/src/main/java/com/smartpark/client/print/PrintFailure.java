package com.smartpark.client.print;

/** A receipt could not be printed. The message is written for the operator. */
public final class PrintFailure extends Exception {

    public enum Reason {
        /** No printer is installed on this computer. */
        NO_PRINTER,
        /** The chosen printer was removed or renamed. */
        PRINTER_NOT_FOUND,
        /** Windows reports the printer offline, paused or not accepting jobs. */
        PRINTER_OFFLINE,
        /** The print job could not be created, rendered or sent. */
        JOB_FAILED
    }

    private final Reason reason;

    public PrintFailure(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PrintFailure(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
