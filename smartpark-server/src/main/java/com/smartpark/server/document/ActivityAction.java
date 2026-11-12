package com.smartpark.server.document;

/** Kinds of events recorded in the activity log. */
public enum ActivityAction {
    USER_CREATED,
    USER_STATUS_CHANGED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    CATEGORY_CREATED,
    SLOT_CREATED,
    SLOT_DISABLED,
    SLOT_ENABLED,
    VEHICLE_ENTRY,
    VEHICLE_EXIT,
    ALLOCATION_REJECTED,
    SLOT_RECONCILED,
    DATA_SEEDED
}
