package com.smartpark.server.security;

import com.smartpark.server.document.UserRole;

/**
 * The caller of a request, resolved from its bearer token.
 * <p>
 * Controllers receive it as a method parameter (see {@link CurrentOperatorArgumentResolver}).
 * The services still re-check that the user exists, is active and has the right role,
 * because an account can be deactivated while a token is still valid.
 *
 * @param role null only in the optional development mode that trusts the X-Operator header
 */
public record AuthenticatedOperator(String username, UserRole role) {
}
