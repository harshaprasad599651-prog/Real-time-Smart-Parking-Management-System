package com.smartpark.client.ui.navigation;

import com.smartpark.client.session.UserRole;

import java.util.Arrays;
import java.util.List;

/** Which role may open which {@link View}, and where each role lands after signing in. */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static boolean canAccess(UserRole role, View view) {
        return role != null && view != null && view.allowedRoles().contains(role);
    }

    /** First screen after login. */
    public static View homeView(UserRole role) {
        return switch (role) {
            case ADMIN -> View.ADMIN_DASHBOARD;
            case ENTRY_OPERATOR -> View.VEHICLE_ENTRY;
            case EXIT_OPERATOR -> View.VEHICLE_EXIT;
        };
    }

    /** Sidebar entries for a role, in declaration order. */
    public static List<View> sidebarViews(UserRole role) {
        return Arrays.stream(View.values()).filter(v -> v.inSidebar() && canAccess(role, v)).toList();
    }
}
