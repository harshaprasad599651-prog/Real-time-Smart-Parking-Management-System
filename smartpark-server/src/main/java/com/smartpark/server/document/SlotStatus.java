package com.smartpark.server.document;

/** Current state of a parking slot. */
public enum SlotStatus {
    /** Free and can be allocated. */
    AVAILABLE,
    /** Allocated to exactly one ACTIVE parking session. */
    OCCUPIED,
    /** Taken out of service by an administrator. */
    DISABLED
}
