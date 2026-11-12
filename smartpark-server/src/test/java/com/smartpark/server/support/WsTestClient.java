package com.smartpark.server.support;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Minimal Java WebSocket client for tests, built on the JDK's {@code java.net.http.WebSocket}.
 * Collects every text message so tests can wait for specific events.
 */
public final class WsTestClient implements WebSocket.Listener, AutoCloseable {

    private final LinkedBlockingQueue<DocumentContext> inbox = new LinkedBlockingQueue<>();
    private final List<DocumentContext> received = new CopyOnWriteArrayList<>();
    private final StringBuilder partial = new StringBuilder();
    private final CompletableFuture<Integer> closed = new CompletableFuture<>();
    private WebSocket webSocket;

    private WsTestClient() {
    }

    /** Connects to {@code ws://localhost:port/ws?operator=...&terminal=...} plus extra query parameters. */
    public static WsTestClient connect(int port, String operator, String terminal, String extraQuery) throws Exception {
        WsTestClient client = new WsTestClient();
        StringBuilder uri = new StringBuilder("ws://localhost:" + port + "/ws?terminal=" + enc(terminal));
        if (operator != null) {
            uri.append("&operator=").append(enc(operator));
        }
        if (extraQuery != null) {
            uri.append('&').append(extraQuery);
        }
        client.webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(uri.toString()), client)
                .get(10, TimeUnit.SECONDS);
        return client;
    }

    // ------------------------------------------------------------- listener

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            DocumentContext json = JsonPath.parse(partial.toString());
            partial.setLength(0);
            received.add(json);
            inbox.add(json);
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        closed.complete(statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        closed.complete(-1);
    }

    // ---------------------------------------------------------------- waits

    /** Waits for the next message of {@code type} that matches the predicate; earlier non-matching messages are skipped. */
    public DocumentContext await(String type, Predicate<DocumentContext> match, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            DocumentContext next = left <= 0 ? null : inbox.poll(left, TimeUnit.NANOSECONDS);
            if (next == null) {
                throw new AssertionError("Timed out waiting for " + type + "; received types: "
                        + received.stream().map(d -> d.<String>read("$.type")).toList());
            }
            if (type.equals(next.read("$.type")) && match.test(next)) {
                return next;
            }
        }
    }

    public DocumentContext await(String type) throws InterruptedException {
        return await(type, d -> true, Duration.ofSeconds(10));
    }

    /** True if a matching message arrives within the timeout. */
    public boolean receives(String type, Predicate<DocumentContext> match, Duration timeout) throws InterruptedException {
        try {
            await(type, match, timeout);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    public List<DocumentContext> received() {
        return received;
    }

    public void sendText(String text) {
        webSocket.sendText(text, true).join();
    }

    /** Close code the server sent, or -1 on error; completes when the connection ends. */
    public CompletableFuture<Integer> closedFuture() {
        return closed;
    }

    /** Simulates a network failure: drops the TCP connection without a WebSocket close handshake. */
    public void abort() {
        webSocket.abort();
    }

    @Override
    public void close() {
        if (!webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").orTimeout(2, TimeUnit.SECONDS)
                    .exceptionally(e -> null).join();
        }
        webSocket.abort();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
