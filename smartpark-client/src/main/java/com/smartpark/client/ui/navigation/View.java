package com.smartpark.client.ui.navigation;

import com.smartpark.client.session.UserRole;
import com.smartpark.client.ui.component.Icons;

import java.util.EnumSet;
import java.util.Set;

import static com.smartpark.client.session.UserRole.ADMIN;
import static com.smartpark.client.session.UserRole.ENTRY_OPERATOR;
import static com.smartpark.client.session.UserRole.EXIT_OPERATOR;

/**
 * Every screen behind the login, with the roles allowed to open it.
 * <p>
 * This enum is the single source of truth for both the sidebar (which buttons a role sees)
 * and the {@link Navigator}'s access check (which screens a role may open). The server
 * enforces the same roles on its endpoints, so hiding a button is never the only protection.
 */
public enum View {

    ADMIN_DASHBOARD("Overview", "Car park status and live activity",
            "/fxml/dashboard/admin-dashboard.fxml", Icons.DASHBOARD, true, EnumSet.of(ADMIN)),

    PARKED_VEHICLES("Parked vehicles", "Every vehicle currently inside",
            "/fxml/admin/parked-vehicles.fxml", Icons.LIST, true, EnumSet.of(ADMIN)),

    PARKING_HISTORY("Parking history", "Previous entries and exits",
            "/fxml/admin/parking-history.fxml", Icons.HISTORY, true, EnumSet.of(ADMIN)),

    SLOT_MANAGEMENT("Slot management", "Add, edit, disable and enable parking slots",
            "/fxml/admin/slot-management.fxml", Icons.GRID, true, EnumSet.of(ADMIN)),

    USER_MANAGEMENT("Users", "Operator accounts and roles",
            "/fxml/admin/user-management.fxml", Icons.GROUP, true, EnumSet.of(ADMIN)),

    ACTIVITY_LOG("Activity", "What entry and exit operators are doing",
            "/fxml/admin/activity-log.fxml", Icons.ACTIVITY, true, EnumSet.of(ADMIN)),

    CONNECTIONS("Terminals", "Operators connected to the central server",
            "/fxml/connections.fxml", Icons.TERMINALS, true, EnumSet.of(ADMIN)),

    VEHICLE_ENTRY("Vehicle Entry", "Register incoming vehicles and assign a slot",
            "/fxml/parking/vehicle-entry.fxml", Icons.ENTRY, true, EnumSet.of(ENTRY_OPERATOR, ADMIN)),

    VEHICLE_EXIT("Vehicle Exit", "Confirm departures and release slots",
            "/fxml/parking/vehicle-exit.fxml", Icons.EXIT, true, EnumSet.of(EXIT_OPERATOR, ADMIN)),

    /** Shown instead of a screen the current role may not open. */
    ACCESS_DENIED("Access denied", "This screen is not available for your role",
            "/fxml/access-denied.fxml", Icons.LOCK, false, EnumSet.allOf(UserRole.class));

    private final String title;
    private final String subtitle;
    private final String fxml;
    private final String icon;
    private final boolean inSidebar;
    private final Set<UserRole> allowedRoles;

    View(String title, String subtitle, String fxml, String icon, boolean inSidebar, Set<UserRole> allowedRoles) {
        this.title = title;
        this.subtitle = subtitle;
        this.fxml = fxml;
        this.icon = icon;
        this.inSidebar = inSidebar;
        this.allowedRoles = allowedRoles;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String fxml() {
        return fxml;
    }

    public String icon() {
        return icon;
    }

    public boolean inSidebar() {
        return inSidebar;
    }

    public Set<UserRole> allowedRoles() {
        return allowedRoles;
    }
}
