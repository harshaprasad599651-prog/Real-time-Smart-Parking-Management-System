package com.smartpark.server.exception;

/** The operator does not exist, is inactive, or lacks the role required for the operation. */
public class OperatorNotAuthorizedException extends SmartParkException {

    public OperatorNotAuthorizedException(String message) {
        super(message);
    }

    public OperatorNotAuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
