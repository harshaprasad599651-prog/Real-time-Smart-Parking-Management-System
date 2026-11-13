package com.smartpark.server.persistence;

/**
 * Names of every index SmartPark creates. Explicit names make duplicate-key errors
 * easy to recognise ({@code E11000 ... index: ux_sessions_active_vehicle}) and let the
 * tests verify each index.
 */
public final class IndexNames {

    public static final String USERS_USERNAME = "ux_users_username";

    public static final String CATEGORIES_NAME = "ux_categories_name";

    public static final String SLOTS_CODE = "ux_slots_code";
    public static final String SLOTS_POSITION = "ux_slots_position";
    public static final String SLOTS_STATUS_CATEGORY = "ix_slots_status_category";

    public static final String SESSIONS_TICKET = "ux_sessions_ticket";
    public static final String SESSIONS_ACTIVE_VEHICLE = "ux_sessions_active_vehicle";
    public static final String SESSIONS_ACTIVE_SLOT = "ux_sessions_active_slot";
    public static final String SESSIONS_VEHICLE_ENTRY = "ix_sessions_vehicle_entry";
    public static final String SESSIONS_STATUS_ENTRY = "ix_sessions_status_entry";

    public static final String ACTIVITY_TIMESTAMP = "ix_activity_timestamp";
    public static final String ACTIVITY_USER_TIMESTAMP = "ix_activity_user_timestamp";
    /** TTL: MongoDB deletes a login session once its expiry time has passed. */
    public static final String LOGIN_SESSIONS_EXPIRY = "ttl_login_sessions_expires";
    public static final String LOGIN_SESSIONS_USERNAME = "ix_login_sessions_username";

    private IndexNames() {
    }
}
