package com.smartpark.server.security;

import com.smartpark.server.exception.UnauthenticatedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Works out who is calling, for both REST requests and WebSocket handshakes.
 * <ol>
 *     <li>{@code Authorization: Bearer <token>}, the normal way after
 *         {@code POST /api/auth/login};</li>
 *     <li>only if {@code smartpark.security.operator-header-enabled=true}: the old
 *         {@code X-Operator: <username>} header. This trusts the caller completely, so it
 *         is <b>off by default</b> and meant only for tests and manual experiments.</li>
 * </ol>
 */
@Component
public class RequestAuthenticator {

    public static final String OPERATOR_HEADER = "X-Operator";
    private static final String BEARER = "Bearer ";

    private final AuthTokenService tokenService;
    private final boolean operatorHeaderEnabled;

    public RequestAuthenticator(AuthTokenService tokenService,
                                @Value("${smartpark.security.operator-header-enabled:false}") boolean operatorHeaderEnabled) {
        this.tokenService = tokenService;
        this.operatorHeaderEnabled = operatorHeaderEnabled;
    }

    /**
     * @param authorization value of the Authorization header (or an access_token query parameter
     *                      wrapped as "Bearer ..."), may be null
     * @param operatorHeader value of X-Operator, may be null
     * @throws UnauthenticatedException if the caller cannot be identified
     */
    public AuthenticatedOperator authenticate(String authorization, String operatorHeader) {
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
                throw new UnauthenticatedException("Unsupported Authorization scheme; use 'Bearer <token>'");
            }
            String token = authorization.substring(BEARER.length()).trim();
            return tokenService.validate(token)
                    .map(s -> new AuthenticatedOperator(s.username(), s.role()))
                    .orElseThrow(() -> new UnauthenticatedException("Session expired or invalid; please sign in again"));
        }
        if (operatorHeaderEnabled && operatorHeader != null && !operatorHeader.isBlank()) {
            return new AuthenticatedOperator(operatorHeader.trim(), null);
        }
        throw new UnauthenticatedException("Sign-in required: send 'Authorization: Bearer <token>' from POST /api/auth/login");
    }

    /** Extracts the raw token from an Authorization header value, if it is a bearer token. */
    public static Optional<String> bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        return Optional.of(authorization.substring(BEARER.length()).trim());
    }

    public boolean operatorHeaderEnabled() {
        return operatorHeaderEnabled;
    }
}
