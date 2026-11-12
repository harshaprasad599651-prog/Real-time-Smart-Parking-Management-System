package com.smartpark.server.websocket;

import com.smartpark.server.document.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * Receives WebSocket lifecycle callbacks and passes them to the {@link RealtimeHub}.
 * <p>
 * The socket is used <b>server to client</b> for events. All commands (park, exit, edit)
 * go through the REST API. The only messages a client sends here are heartbeats:
 * WebSocket ping frames, or the text {@code PING}, which is answered with a PONG message.
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final RealtimeHub hub;

    public RealtimeWebSocketHandler(RealtimeHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> a = session.getAttributes();
        hub.register(session,
                (String) a.get(RealtimeHandshakeInterceptor.ATTR_CLIENT_ID),
                (String) a.get(RealtimeHandshakeInterceptor.ATTR_OPERATOR),
                (UserRole) a.get(RealtimeHandshakeInterceptor.ATTR_ROLE),
                (String) a.get(RealtimeHandshakeInterceptor.ATTR_TERMINAL),
                (Long) a.get(RealtimeHandshakeInterceptor.ATTR_SINCE),
                (String) a.get(RealtimeHandshakeInterceptor.ATTR_INSTANCE));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        hub.touch(session.getId());
        String text = message.getPayload().trim();
        if (text.equalsIgnoreCase("PING") || text.contains("\"PING\"")) {
            hub.sendPong(session.getId());
        }
        // Anything else is ignored: commands must use the REST API.
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        hub.touch(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session.getId(), status);
    }
}
