package com.smartpark.server.security;

import com.smartpark.server.document.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a controller method (or every method of a controller) to the listed roles.
 * Enforced on the server by {@link RoleCheckInterceptor}, before the method runs:
 * no credentials gives 401; an inactive account or another role gives 403.
 * <p>
 * An {@code /api} endpoint without this annotation (and without {@link PublicEndpoint})
 * is open to <b>any signed-in, active</b> user.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();
}
