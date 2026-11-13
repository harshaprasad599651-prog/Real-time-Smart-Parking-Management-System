package com.smartpark.server.websocket;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.websocket.RealtimePayloads.ConnectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One connected client and its outgoing message queue.
 * <p>
 * A Spring/Tomcat {@link WebSocketSession} must not be written to by two threads at once.
 * Each client therefore gets its own queue and a single (virtual) sender thread:
 * <ul>
 *     <li>messages reach the client in exactly the order they were enqueued;</li>
 *     <li>a slow or stalled client only delays itself, never the broadcaster or other clients;</li>
 *     <li>if a client falls more than {@value #QUEUE_LIMIT} messages behind it is disconnected. It
 *         will reconnect and catch up through replay or resync.</li>
 * </ul>
 */
final class ClientConnection {

    static final int QUEUE_LIMIT = 1_000;
    private static final Logger log = LoggerFactory.getLogger(ClientConnection.class);

    private final WebSocketSession session;
    private final String clientId;
    private final String operator;
    private final UserRole role;
    private final String terminal;
    private final String remoteAddress;
    private final Instant connectedAt;
    private volatile Instant lastSeenAt;
    private volatile boolean stopped;

    private final BlockingQueue<WebSocketMessage<?>> outbox = new LinkedBlockingQueue<>(QUEUE_LIMIT);
    private Thread sender;

    ClientConnection(WebSocketSession session, String clientId, String operator, UserRole role, String terminal) {
        this.session = session;
        this.clientId = clientId;
        this.operator = operator;
        this.role = role;
        this.terminal = terminal;
        this.remoteAddress = session.getRemoteAddress() == null ? "?" : session.getRemoteAddress().toString();
        this.connectedAt = Instant.now();
        this.lastSeenAt = connectedAt;
    }

    void start() {
        sender = Thread.ofVirtual().name("ws-send-" + operator + "-" + session.getId()).start(this::sendLoop);
    }

    /** Queues a message without blocking. Disconnects the client if it is hopelessly behind. */
    void enqueue(WebSocketMessage<?> message) {
        if (stopped) {
            return;
        }
        if (!outbox.offer(message)) {
            log.warn("Client {} ({}) is too slow: {} messages queued; disconnecting", operator, session.getId(), QUEUE_LIMIT);
            close(CloseStatus.SESSION_NOT_RELIABLE.withReason("Too slow - reconnect to catch up"));
        }
    }

    private void sendLoop() {
        try {
            while (!stopped) {
                WebSocketMessage<?> message = outbox.poll(1, TimeUnit.SECONDS);
                if (message != null && session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalStateException e) {
            log.debug("Send to {} ({}) failed: {}", operator, session.getId(), e.getMessage());
            close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    void stop() {
        stopped = true;
        outbox.clear();
        if (sender != null) {
            sender.interrupt();
        }
    }

    void close(CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.debug("Closing {} failed: {}", session.getId(), e.getMessage());
        }
    }

    void touch() {
        lastSeenAt = Instant.now();
    }

    Instant lastSeenAt() {
        return lastSeenAt;
    }

    String sessionId() {
        return session.getId();
    }

    String clientId() {
        return clientId;
    }

    String operator() {
        return operator;
    }

    String terminal() {
        return terminal;
    }

    UserRole role() {
        return role;
    }

    ConnectionInfo info() {
        return new ConnectionInfo(session.getId(), clientId, operator, role, terminal, remoteAddress, connectedAt, lastSeenAt);
    }
}
