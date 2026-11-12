package com.smartpark.server.websocket;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.websocket.RealtimePayloads.ConnectionAck;
import com.smartpark.server.websocket.RealtimePayloads.ConnectionInfo;
import com.smartpark.server.websocket.RealtimePayloads.OperatorPresence;
import com.smartpark.server.websocket.RealtimePayloads.RealtimeStatus;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central real-time hub: keeps track of connected clients and broadcasts events to all of them.
 *
 * <h2>Ordering and duplicates</h2>
 * Every broadcast event gets the next {@code sequence} number under a lock and is queued
 * for every client in the same critical section. So all clients receive all events in
 * the same order, numbered 1, 2, 3 ... A client that sees a sequence number it already
 * processed can safely ignore it.
 *
 * <h2>Reconnection</h2>
 * The last {@value #HISTORY_SIZE} events are kept in memory. A client reconnecting with
 * {@code ?since=<last sequence>&instance=<serverInstanceId>} gets the events it missed
 * replayed right after its CONNECTION_ACK. If it was away too long, or the server was
 * restarted (different {@code serverInstanceId}), the ACK says {@code resyncRequired=true}
 * and the client reloads its state through the REST API.
 *
 * <h2>Connection monitoring</h2>
 * Every {@value #PING_SECONDS} s each client is sent a WebSocket ping. Any message or pong
 * from the client updates {@code lastSeenAt}. A client silent for {@value #DEAD_AFTER_SECONDS} s
 * (cable pulled, Wi-Fi lost, PC asleep) is closed and reported as OPERATOR_DISCONNECTED.
 */
@Component
public class RealtimeHub {

    static final int HISTORY_SIZE = 1_000;
    static final int PING_SECONDS = 20;
    static final int DEAD_AFTER_SECONDS = 60;
    static final int CLIENT_HEARTBEAT_SECONDS = 15;

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private final String serverInstanceId = UUID.randomUUID().toString().substring(0, 8);
    private final JsonMapper jsonMapper;
    private final Duration deadAfter;

    private final Object lock = new Object();
    private long sequence;                                             // guarded by lock
    private final Deque<Stored> history = new ArrayDeque<>();          // guarded by lock
    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    private record Stored(long sequence, String json) {
    }

    public RealtimeHub(JsonMapper jsonMapper,
                       @Value("${smartpark.realtime.dead-after:PT" + DEAD_AFTER_SECONDS + "S}") Duration deadAfter) {
        this.jsonMapper = jsonMapper;
        this.deadAfter = deadAfter;
    }

    // ------------------------------------------------------------- broadcasting

    /** Sends an event to every connected client. Returns the event with its assigned sequence. */
    public RealtimeEvent broadcast(RealtimeEventType type, String actor, Object payload) {
        if (!type.isBroadcast()) {
            throw new IllegalArgumentException(type + " is not a broadcast event");
        }
        synchronized (lock) {
            RealtimeEvent event = new RealtimeEvent(UUID.randomUUID().toString(), ++sequence, serverInstanceId,
                    type, Instant.now(), actor, terminalOf(actor), payload);
            String json = toJson(event);
            history.addLast(new Stored(event.sequence(), json));
            while (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
            TextMessage message = new TextMessage(json);
            clients.values().forEach(c -> c.enqueue(message));
            log.debug("Broadcast #{} {} by {} to {} client(s)", event.sequence(), type, actor, clients.size());
            return event;
        }
    }

    // ------------------------------------------------------- connect/disconnect

    /**
     * Registers a newly connected client, sends it a CONNECTION_ACK followed by any missed
     * events, then announces OPERATOR_CONNECTED to everyone.
     *
     * @param since    last sequence the client processed before reconnecting, or null for a fresh client
     * @param instance serverInstanceId the client was connected to before, or null
     */
    void register(WebSocketSession session, String clientId, String operator, UserRole role, String terminal,
                  Long since, String instance) {
        ClientConnection client = new ClientConnection(session, clientId, operator, role, terminal);
        client.start();

        ClientConnection previous = null;
        synchronized (lock) {
            // One live connection per client process: a reconnect replaces a half-open old connection.
            for (ClientConnection existing : clients.values()) {
                if (existing.clientId().equals(clientId)) {
                    previous = existing;
                }
            }

            List<Stored> replay = List.of();
            boolean resync = false;
            String resyncReason = null;
            if (since != null && instance != null) {
                long oldestKept = history.isEmpty() ? sequence + 1 : history.peekFirst().sequence();
                if (!serverInstanceId.equals(instance)) {
                    resync = true;
                    resyncReason = "SERVER_RESTARTED";
                } else if (since < oldestKept - 1) {
                    resync = true;
                    resyncReason = "TOO_MANY_MISSED_EVENTS";
                } else {
                    replay = history.stream().filter(s -> s.sequence() > since).toList();
                }
            }

            List<ConnectionInfo> others = clients.values().stream().map(ClientConnection::info)
                    .sorted(Comparator.comparing(ConnectionInfo::connectedAt)).toList();
            ConnectionAck ack = new ConnectionAck(session.getId(), serverInstanceId, sequence, replay.size(),
                    resync, resyncReason, CLIENT_HEARTBEAT_SECONDS, others);
            client.enqueue(new TextMessage(toJson(control(RealtimeEventType.CONNECTION_ACK, operator, ack))));
            replay.forEach(s -> client.enqueue(new TextMessage(s.json())));
            clients.put(session.getId(), client);

            log.info("Realtime client connected: {} ({}) from {} terminal={} [replayed {}, resync={}]",
                    operator, role, client.info().remoteAddress(), terminal, replay.size(), resync);

            // Still inside the (re-entrant) lock, so presence events are numbered in the order
            // in which clients really connected and the client count is exact.
            broadcast(RealtimeEventType.OPERATOR_CONNECTED, operator,
                    new OperatorPresence(client.info(), clients.size(), null));
        }
        if (previous != null) {
            log.info("Client {} reconnected; closing its previous connection {}", clientId, previous.sessionId());
            previous.close(CloseStatus.POLICY_VIOLATION.withReason("Replaced by a newer connection"));
        }
    }

    void unregister(String sessionId, CloseStatus status) {
        synchronized (lock) {
            ClientConnection client = clients.remove(sessionId);
            if (client == null) {
                return; // already removed (e.g. heartbeat timeout followed by the close callback)
            }
            client.stop();
            log.info("Realtime client disconnected: {} ({}) status={}", client.operator(), sessionId, status);
            if (!shuttingDown) {
                String reason = status.getReason() != null ? status.getCode() + " " + status.getReason()
                        : String.valueOf(status.getCode());
                broadcast(RealtimeEventType.OPERATOR_DISCONNECTED, client.operator(),
                        new OperatorPresence(client.info(), clients.size(), reason));
            }
        }
    }

    /** Records that the client is alive (any inbound message or pong). */
    void touch(String sessionId) {
        ClientConnection client = clients.get(sessionId);
        if (client != null) {
            client.touch();
        }
    }

    /** Replies to an application-level PING. */
    void sendPong(String sessionId) {
        ClientConnection client = clients.get(sessionId);
        if (client != null) {
            client.enqueue(new TextMessage(toJson(control(RealtimeEventType.PONG, client.operator(), null))));
        }
    }

    // --------------------------------------------------------------- heartbeat

    /** Pings every client and closes the ones that have been silent too long. */
    @Scheduled(fixedDelayString = "${smartpark.realtime.ping-interval:PT" + PING_SECONDS + "S}")
    void heartbeat() {
        Instant deadline = Instant.now().minus(deadAfter);
        for (ClientConnection client : new ArrayList<>(clients.values())) {
            if (client.lastSeenAt().isBefore(deadline)) {
                log.warn("Realtime client {} ({}) silent since {}; closing", client.operator(),
                        client.sessionId(), client.lastSeenAt());
                client.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("Heartbeat timeout"));
                unregister(client.sessionId(), CloseStatus.SESSION_NOT_RELIABLE.withReason("Heartbeat timeout"));
            } else {
                client.enqueue(new PingMessage(ByteBuffer.wrap(serverInstanceId.getBytes())));
            }
        }
    }

    // ----------------------------------------------------------------- status

    public RealtimeStatus status() {
        List<ConnectionInfo> infos = clients.values().stream().map(ClientConnection::info)
                .sorted(Comparator.comparing(ConnectionInfo::connectedAt)).toList();
        synchronized (lock) {
            return new RealtimeStatus(serverInstanceId, sequence, infos.size(), infos);
        }
    }

    public String serverInstanceId() {
        return serverInstanceId;
    }

    @PreDestroy
    void shutdown() {
        shuttingDown = true;
        clients.values().forEach(c -> c.close(CloseStatus.GOING_AWAY.withReason("Server shutting down")));
    }

    // ----------------------------------------------------------------- helpers

    private RealtimeEvent control(RealtimeEventType type, String actor, Object payload) {
        return new RealtimeEvent(UUID.randomUUID().toString(), 0, serverInstanceId, type, Instant.now(),
                actor, null, payload);
    }

    /** Terminal from which the operator is connected, if any (shown on the admin dashboard). */
    private String terminalOf(String operator) {
        if (operator == null) {
            return null;
        }
        return clients.values().stream().filter(c -> operator.equals(c.operator()))
                .map(ClientConnection::terminal).findFirst().orElse(null);
    }

    private String toJson(RealtimeEvent event) {
        return jsonMapper.writeValueAsString(event);
    }
}
