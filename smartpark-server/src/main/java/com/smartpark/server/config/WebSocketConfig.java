package com.smartpark.server.config;

import com.smartpark.server.websocket.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the plain (non-STOMP) WebSocket endpoint used for real-time
 * updates. Clients connect to {@code ws://<server-ip>:8080/ws}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String REALTIME_ENDPOINT = "/ws";

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;

    public WebSocketConfig(RealtimeWebSocketHandler realtimeWebSocketHandler) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Desktop clients do not send a browser Origin header, so this is permissive.
        // Authentication of the handshake will be added together with login.
        registry.addHandler(realtimeWebSocketHandler, REALTIME_ENDPOINT)
                .setAllowedOriginPatterns("*");
    }
}
