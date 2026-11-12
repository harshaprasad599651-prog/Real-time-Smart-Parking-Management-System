package com.smartpark.server.websocket;

import com.smartpark.server.dto.UserDto;
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
 * Clients identify themselves like the REST API does, with the {@code X-Operator} header
 * (or an {@code operator} query parameter), plus optional query parameters:
 * <pre>
 * ws://server:8080/ws?operator=entry1&amp;terminal=ENTRY-GATE-1&amp;clientId=&lt;uuid&gt;&amp;since=42&amp;instance=a1b2c3d4
 * </pre>
 * Unknown or inactive operators are refused with HTTP 403, so the connection is never upgraded.
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

    public RealtimeHandshakeInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String operator = Optional.ofNullable(request.getHeaders().getFirst("X-Operator"))
                .orElse(params.getFirst("operator"));

        Optional<UserDto> user = operator == null || operator.isBlank()
                ? Optional.empty() : userService.findByUsername(operator).filter(UserDto::active);
        if (user.isEmpty()) {
            log.warn("Refused WebSocket handshake from {}: unknown or inactive operator '{}'",
                    request.getRemoteAddress(), operator);
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
