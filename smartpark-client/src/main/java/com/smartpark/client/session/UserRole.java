package com.smartpark.client.session;

/** The three SmartPark roles, as defined by the server. */
public enum UserRole {
    ADMIN("Administrator"),
    ENTRY_OPERATOR("Entry operator"),
    EXIT_OPERATOR("Exit operator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
