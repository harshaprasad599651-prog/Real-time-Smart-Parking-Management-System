package com.smartpark.server.document;

/** Lifecycle of a parking session (one ticket). */
public enum SessionStatus {
    /** Vehicle is currently parked. */
    ACTIVE,
    /** Vehicle has left and the slot was released. */
    COMPLETED
}
