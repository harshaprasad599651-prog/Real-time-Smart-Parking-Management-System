package com.smartpark.client.net;

import com.smartpark.client.config.ClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin wrapper around the JDK {@link HttpClient} for talking to the SmartPark server.
 * <p>
 * All calls are asynchronous so the JavaFX UI thread never blocks on the network.
 */
public class ServerApiClient implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final URI baseUri;
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public ServerApiClient(ClientConfig config) {
        this.baseUri = config.httpBaseUri();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(executor)
                .build();
    }

    /**
     * Calls {@code GET /api/health}.
     *
     * @return a future that completes with the raw JSON body when the server answers 200,
     *         or completes exceptionally when it is unreachable or returns an error.
     */
    public CompletableFuture<String> checkHealth() {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/health"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ServerApiException("Server returned HTTP " + response.statusCode());
                    }
                    return response.body();
                });
    }

    /** {@code GET path}; the future completes with the response whatever its status code. */
    public CompletableFuture<HttpResponse<String>> get(String path) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /** {@code POST path} with a JSON body, identifying the operator with the {@code X-Operator} header. */
    public CompletableFuture<HttpResponse<String>> post(String path, String operator, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Operator", operator)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public URI baseUri() {
        return baseUri;
    }

    @Override
    public void close() {
        httpClient.close();
        executor.shutdownNow();
    }
}
