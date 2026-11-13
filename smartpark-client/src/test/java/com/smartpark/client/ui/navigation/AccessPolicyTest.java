package com.smartpark.client.ui.navigation;

import com.smartpark.client.session.UserRole;
import org.junit.jupiter.api.Test;

import static com.smartpark.client.ui.navigation.View.ACCESS_DENIED;
import static com.smartpark.client.ui.navigation.View.ADMIN_DASHBOARD;
import static com.smartpark.client.ui.navigation.View.CONNECTIONS;
import static com.smartpark.client.ui.navigation.View.VEHICLE_ENTRY;
import static com.smartpark.client.ui.navigation.View.VEHICLE_EXIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {

    @Test
    void eachRoleLandsOnItsOwnDashboard() {
        assertEquals(ADMIN_DASHBOARD, AccessPolicy.homeView(UserRole.ADMIN));
        assertEquals(VEHICLE_ENTRY, AccessPolicy.homeView(UserRole.ENTRY_OPERATOR));
        assertEquals(VEHICLE_EXIT, AccessPolicy.homeView(UserRole.EXIT_OPERATOR));
    }

    @Test
    void operatorsCannotOpenOtherRolesScreens() {
        assertFalse(AccessPolicy.canAccess(UserRole.ENTRY_OPERATOR, VEHICLE_EXIT));
        assertFalse(AccessPolicy.canAccess(UserRole.ENTRY_OPERATOR, ADMIN_DASHBOARD));
        assertFalse(AccessPolicy.canAccess(UserRole.ENTRY_OPERATOR, CONNECTIONS));
        assertFalse(AccessPolicy.canAccess(UserRole.EXIT_OPERATOR, VEHICLE_ENTRY));
        assertFalse(AccessPolicy.canAccess(UserRole.EXIT_OPERATOR, ADMIN_DASHBOARD));
        assertFalse(AccessPolicy.canAccess(null, VEHICLE_ENTRY));
    }

    @Test
    void adminCanOpenEverything() {
        for (View v : View.values()) {
            assertTrue(AccessPolicy.canAccess(UserRole.ADMIN, v), v.name());
        }
    }

    @Test
    void sidebarShowsOnlyAllowedScreens() {
        assertEquals(java.util.List.of(ADMIN_DASHBOARD, View.PARKED_VEHICLES, View.PARKING_HISTORY,
                        View.SLOT_MANAGEMENT, View.USER_MANAGEMENT, View.ACTIVITY_LOG, CONNECTIONS, VEHICLE_ENTRY, VEHICLE_EXIT),
                AccessPolicy.sidebarViews(UserRole.ADMIN));
        for (View adminOnly : java.util.List.of(View.PARKED_VEHICLES, View.PARKING_HISTORY, View.SLOT_MANAGEMENT,
                View.USER_MANAGEMENT, View.ACTIVITY_LOG, CONNECTIONS)) {
            assertFalse(AccessPolicy.canAccess(UserRole.ENTRY_OPERATOR, adminOnly), adminOnly.name());
            assertFalse(AccessPolicy.canAccess(UserRole.EXIT_OPERATOR, adminOnly), adminOnly.name());
        }
        assertEquals(java.util.List.of(VEHICLE_ENTRY), AccessPolicy.sidebarViews(UserRole.ENTRY_OPERATOR));
        assertEquals(java.util.List.of(VEHICLE_EXIT), AccessPolicy.sidebarViews(UserRole.EXIT_OPERATOR));
        assertFalse(AccessPolicy.sidebarViews(UserRole.ADMIN).contains(ACCESS_DENIED));
    }
}
