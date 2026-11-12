package com.smartpark.server.config;

import com.smartpark.server.websocket.RealtimeHandshakeInterceptor;
import com.smartpark.server.websocket.RealtimeWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Registers the plain (non-STOMP) WebSocket endpoint used for real-time updates:
 * {@code ws://<server-ip>:8080/ws}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String REALTIME_ENDPOINT = "/ws";

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(RealtimeWebSocketHandler realtimeWebSocketHandler,
                           RealtimeHandshakeInterceptor handshakeInterceptor) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Desktop (Java) clients send no browser Origin header; the operator check happens in the interceptor.
        registry.addHandler(realtimeWebSocketHandler, REALTIME_ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * Tomcat WebSocket limits: small messages, and drop connections idle for 2 minutes.
     * Only when a servlet container is running (not in non-web test contexts).
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }
}
