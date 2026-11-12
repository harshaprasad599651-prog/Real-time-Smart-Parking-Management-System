package com.smartpark.server.controller;

import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.service.UserService;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Identifies who performs a write operation.
 * <p>
 * <b>Interim mechanism:</b> until login/token authentication is implemented, every write
 * request names its operator in the {@value #HEADER} header. The operator must exist,
 * be active and have a suitable role. Only this class will change when real
 * authentication replaces the header.
 */
@Component
public class OperatorGuard {

    public static final String HEADER = "X-Operator";

    private final UserService userService;

    public OperatorGuard(UserService userService) {
        this.userService = userService;
    }

    /** @return the normalised username of an active ADMIN */
    public String requireAdmin(String operatorHeader) {
        User user = userService.requireOperator(operatorHeader, EnumSet.of(UserRole.ADMIN));
        return user.getUsername();
    }
}
