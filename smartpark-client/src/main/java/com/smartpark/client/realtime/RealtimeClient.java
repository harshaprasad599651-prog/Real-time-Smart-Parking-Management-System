package com.smartpark.client.realtime;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a WebSocket connection to {@code ws://server:port/ws} alive and delivers events.
 *
 * <h2>Connection monitoring</h2>
 * Sends a WebSocket ping every {@code heartbeatSeconds} (15 s, set by the server). If nothing
 * at all (event, pong, server ping) arrives for 3 heartbeats, the connection is treated as
 * dead, even if TCP has not noticed yet (e.g. Wi-Fi dropped), and is re-established.
 *
 * <h2>Reconnection</h2>
 * Retries with exponential back-off: 1 s, 2 s, 4 s ... up to 30 s, with random jitter so
 * several terminals don't all retry at the same instant. The reconnect URL carries
 * {@code since=<last sequence>&instance=<server id>}, so the server replays the missed events.
 * A stable {@code clientId} lets the server replace a half-open old connection.
 *
 * <h2>Duplicates</h2>
 * {@link EventSequenceTracker} drops anything already processed, so each event reaches the
 * listeners at most once and in server order.
 */
public final class RealtimeClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RealtimeClient.class.getName());
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final URI endpoint;
    private final String accessToken;
    private final String operator;
    private final String terminal;
    private final String clientId = UUID.randomUUID().toString();
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final EventSequenceTracker tracker = new EventSequenceTracker();
    private final List<RealtimeListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemon("realtime-scheduler"));
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(daemon("realtime-dispatch"));

    private volatile WebSocket socket;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile Instant lastInbound = Instant.now();
    private volatile int heartbeatSeconds = 15;
    private volatile boolean closed;
    private final AtomicBoolean reconnectPending = new AtomicBoolean();
    private int attempt;                                // guarded by scheduler thread
    private boolean everConnected;

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();

    /** Statistics for monitoring and for the test client. */
    public record Stats(long received, long delivered, long duplicatesDropped, long reconnects, long lastSequence) {
    }

    /**
     * @param endpoint    WebSocket URL, e.g. {@code ws://192.168.1.10:8080/ws}
     * @param accessToken bearer token from {@code POST /api/auth/login}, sent in the handshake
     * @param operator    username (for display and logging only; the server trusts the token)
     * @param terminal    terminal name shown to the admin, e.g. {@code ENTRY-GATE-1}
     */
    public RealtimeClient(URI endpoint, String accessToken, String operator, String terminal) {
        this.endpoint = endpoint;
        this.accessToken = accessToken;
        this.operator = operator;
        this.terminal = terminal;
    }

    public RealtimeClient addListener(RealtimeListener listener) {
        listeners.add(listener);
        return this;
    }

    /** Starts connecting in the background. Returns immediately; watch {@link RealtimeListener#onStateChanged}. */
    public void connect() {
        if (closed) {
            throw new IllegalStateException("Client is closed");
        }
        setState(ConnectionState.CONNECTING, endpoint.toString());
        scheduler.execute(this::openSocket);
        scheduler.scheduleWithFixedDelay(this::heartbeat, 5, 5, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------- connecting

    private void openSocket() {
        if (closed) {
            return;
        }
        reconnectPending.set(false);
        URI uri = connectUri();
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + accessToken)
                .buildAsync(uri, new SocketListener())
                .whenComplete((ws, error) -> {
                    int refused = handshakeStatus(error);
                    if (refused == 401 || refused == 403) {
                        // Credentials rejected: retrying cannot help. The application must sign in again.
                        closed = true;
                        setState(ConnectionState.UNAUTHORIZED, "server refused the session (HTTP " + refused + ")");
                    } else if (error != null) {
                        scheduleReconnect("connect failed: " + rootMessage(error));
                    } else if (closed) {
                        ws.abort();
                    } else {
                        socket = ws;
                        lastInbound = Instant.now();
                        scheduler.execute(() -> attempt = 0);
                        setState(ConnectionState.CONNECTED, uri.getHost() + ":" + uri.getPort());
                    }
                });
    }

    private URI connectUri() {
        StringBuilder q = new StringBuilder(endpoint.toString())
                .append(endpoint.getQuery() == null ? '?' : '&')
                .append("terminal=").append(enc(terminal))
                .append("&clientId=").append(clientId);
        if (tracker.serverInstanceId() != null) {
            q.append("&since=").append(tracker.lastSequence())
                    .append("&instance=").append(tracker.serverInstanceId());
        }
        return URI.create(q.toString());
    }

    private void scheduleReconnect(String reason) {
        if (closed || !reconnectPending.compareAndSet(false, true)) {
            return; // already closing or a retry is already scheduled
        }
        socket = null;
        scheduler.execute(() -> {
            long base = Math.min(MAX_BACKOFF.toMillis(), 1000L << Math.min(attempt, 5));
            long delay = base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1); // jitter
            attempt++;
            reconnects.incrementAndGet();
            setState(ConnectionState.RECONNECTING, reason + " - retry in " + delay + " ms");
            scheduler.schedule(this::openSocket, delay, TimeUnit.MILLISECONDS);
        });
    }

    private void heartbeat() {
        WebSocket ws = socket;
        if (ws == null || state != ConnectionState.CONNECTED) {
            return;
        }
        Duration silence = Duration.between(lastInbound, Instant.now());
        if (silence.toSeconds() >= 3L * heartbeatSeconds) {
            LOG.warning(() -> "No data from server for " + silence.toSeconds() + " s; reconnecting");
            ws.abort();
            disconnected(ws, "heartbeat timeout");
        } else if (silence.toSeconds() >= heartbeatSeconds - 5) {
            ws.sendPing(ByteBuffer.wrap(clientId.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void disconnected(WebSocket ws, String reason) {
        if (ws == socket || socket == null) { // ignore callbacks from an already replaced socket
            scheduleReconnect(reason);
        }
    }

    // --------------------------------------------------------- incoming data

    private void handleText(String text) {
        lastInbound = Instant.now();
        RealtimeEvent event;
        try {
            event = json.readValue(text, RealtimeEvent.class);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Ignoring unreadable message: " + text, e);
            return;
        }
        received.incrementAndGet();
        switch (event.type()) {
            case "CONNECTION_ACK" -> {
                ConnectionAck ack = json.treeToValue(event.payload(), ConnectionAck.class);
                heartbeatSeconds = Math.max(5, ack.heartbeatSeconds());
                boolean fresh = tracker.onAck(ack);
                String reason = !everConnected ? "INITIAL_CONNECTION"
                        : ack.resyncRequired() ? ack.resyncReason()
                        : fresh ? "SERVER_RESTARTED" : null;
                everConnected = true;
                dispatch(l -> l.onConnected(ack));
                if (reason != null) {
                    dispatch(l -> l.onResyncRequired(reason));
                }
            }
            case "PONG" -> { /* liveness only */ }
            default -> {
                switch (tracker.accept(event)) {
                    case DUPLICATE -> duplicates.incrementAndGet();
                    case DELIVER_AFTER_GAP -> {
                        dispatch(l -> l.onResyncRequired("SEQUENCE_GAP"));
                        deliver(event);
                    }
                    case DELIVER -> deliver(event);
                }
            }
        }
    }

    private void deliver(RealtimeEvent event) {
        delivered.incrementAndGet();
        dispatch(l -> l.onEvent(event));
    }

    private final class SocketListener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                handleText(text);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
            lastInbound = Instant.now(); // the JDK answers the ping with a pong automatically
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
            lastInbound = Instant.now();
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            disconnected(ws, "closed by server (" + statusCode + (reason.isBlank() ? "" : " " + reason) + ")");
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            disconnected(ws, "network error: " + rootMessage(error));
        }
    }

    // -------------------------------------------------------------- utilities

    /**
     * Drops the TCP connection without a close handshake, like a pulled network cable.
     * Used by the test client to demonstrate automatic reconnection and replay.
     */
    public void simulateNetworkFailure() {
        WebSocket ws = socket;
        if (ws != null) {
            ws.abort();
            disconnected(ws, "simulated network failure");
        }
    }

    public ConnectionState state() {
        return state;
    }

    public String operator() {
        return operator;
    }

    public String terminal() {
        return terminal;
    }

    public Stats stats() {
        return new Stats(received.get(), delivered.get(), duplicates.get(), reconnects.get(), tracker.lastSequence());
    }

    private void setState(ConnectionState newState, String detail) {
        state = newState;
        dispatch(l -> l.onStateChanged(newState, detail));
    }

    private void dispatch(Consumer<RealtimeListener> call) {
        if (dispatcher.isShutdown()) {
            return;
        }
        dispatcher.execute(() -> listeners.forEach(l -> {
            try {
                call.accept(l);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Realtime listener failed", e);
            }
        }));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        WebSocket ws = socket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "client closed")
                    .orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((r, e) -> ws.abort());
        }
        setState(ConnectionState.CLOSED, "closed");
        scheduler.shutdownNow();
        dispatcher.shutdown();
        try {
            dispatcher.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        http.close();
    }

    /** HTTP status of a refused WebSocket handshake, or -1. */
    private static int handshakeStatus(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.net.http.WebSocketHandshakeException h) {
                return h.getResponse().statusCode();
            }
        }
        return -1;
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
