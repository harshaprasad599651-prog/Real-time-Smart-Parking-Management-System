package com.smartpark.server.document;

/** Kinds of events recorded in the activity log. */
public enum ActivityAction {
    USER_CREATED,
    USER_STATUS_CHANGED,
    USER_ROLE_CHANGED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    SLOT_CREATED,
    SLOT_UPDATED,
    SLOT_DISABLED,
    SLOT_ENABLED,
    VEHICLE_ENTRY,
    VEHICLE_EXIT,
    RECEIPT_PRINTED,
    RECEIPT_REPRINTED,
    ALLOCATION_REJECTED,
    SLOT_RECONCILED,
    DATA_SEEDED
}
