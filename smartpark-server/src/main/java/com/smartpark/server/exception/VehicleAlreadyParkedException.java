package com.smartpark.server.exception;

/** The vehicle already has an ACTIVE parking session. */
public class VehicleAlreadyParkedException extends SmartParkException {

    public VehicleAlreadyParkedException(String message) {
        super(message);
    }

    public VehicleAlreadyParkedException(String message, Throwable cause) {
        super(message, cause);
    }
}
