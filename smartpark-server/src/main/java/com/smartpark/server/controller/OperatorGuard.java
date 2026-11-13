package com.smartpark.server.controller;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.service.UserService;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Role checks for management endpoints. The caller has already been authenticated
 * (bearer token); this checks against the database that the account is still active
 * and has the ADMIN role.
 */
@Component
public class OperatorGuard {

    private final UserService userService;

    public OperatorGuard(UserService userService) {
        this.userService = userService;
    }

    /** @return the normalised username of an active ADMIN, otherwise 403 OPERATOR_NOT_AUTHORIZED */
    public String requireAdmin(AuthenticatedOperator operator) {
        return userService.requireOperator(operator.username(), EnumSet.of(UserRole.ADMIN)).getUsername();
    }
}
