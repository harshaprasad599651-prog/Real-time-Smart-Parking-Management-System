package com.smartpark.server.websocket;

import com.smartpark.server.dto.UserDto;
import com.smartpark.server.exception.UnauthenticatedException;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.security.RequestAuthenticator;
import org.springframework.http.HttpHeaders;
import com.smartpark.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks the WebSocket opening handshake (an HTTP GET with {@code Upgrade: websocket}).
 * <p>
 * Clients authenticate like the REST API does, with {@code Authorization: Bearer <token>}
 * (or an {@code access_token} query parameter for clients that cannot set headers), plus
 * optional query parameters:
 * <pre>
 * ws://server:8080/ws?terminal=ENTRY-GATE-1&amp;clientId=&lt;uuid&gt;&amp;since=42&amp;instance=a1b2c3d4
 * </pre>
 * Without a valid token the handshake is refused with HTTP 401, and for an inactive account
 * with 403, so the connection is never upgraded. (If the development-only operator header mode
 * is enabled, {@code X-Operator} or {@code ?operator=} is also accepted.)
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_OPERATOR = "smartpark.operator";
    static final String ATTR_ROLE = "smartpark.role";
    static final String ATTR_TERMINAL = "smartpark.terminal";
    static final String ATTR_CLIENT_ID = "smartpark.clientId";
    static final String ATTR_SINCE = "smartpark.since";
    static final String ATTR_INSTANCE = "smartpark.instance";

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandshakeInterceptor.class);

    private final UserService userService;
    private final RequestAuthenticator authenticator;

    public RealtimeHandshakeInterceptor(UserService userService, RequestAuthenticator authenticator) {
        this.userService = userService;
        this.authenticator = authenticator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String authorization = Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .orElseGet(() -> params.getFirst("access_token") == null ? null : "Bearer " + params.getFirst("access_token"));
        String operatorHeader = Optional.ofNullable(request.getHeaders().getFirst(RequestAuthenticator.OPERATOR_HEADER))
                .orElse(params.getFirst("operator"));

        AuthenticatedOperator caller;
        try {
            caller = authenticator.authenticate(authorization, operatorHeader);
        } catch (UnauthenticatedException e) {
            log.warn("Refused WebSocket handshake from {}: {}", request.getRemoteAddress(), e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Optional<UserDto> user = userService.findByUsername(caller.username()).filter(UserDto::active);
        if (user.isEmpty()) {
            log.warn("Refused WebSocket handshake from {}: unknown or inactive operator '{}'",
                    request.getRemoteAddress(), caller.username());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(ATTR_OPERATOR, user.get().username());
        attributes.put(ATTR_ROLE, user.get().role());
        attributes.put(ATTR_TERMINAL, Optional.ofNullable(params.getFirst("terminal")).orElse("UNKNOWN"));
        attributes.put(ATTR_CLIENT_ID, Optional.ofNullable(params.getFirst("clientId"))
                .orElse(UUID.randomUUID().toString()));
        attributes.put(ATTR_INSTANCE, params.getFirst("instance"));
        String since = params.getFirst("since");
        if (since != null) {
            try {
                attributes.put(ATTR_SINCE, Long.parseLong(since));
            } catch (NumberFormatException e) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
