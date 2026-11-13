package com.smartpark.client.net;

import com.smartpark.client.config.ClientConfig;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Reusable REST client for the SmartPark server, built on the JDK {@link HttpClient}.
 * <ul>
 *     <li>All calls are <b>asynchronous</b> ({@link CompletableFuture}), so the JavaFX
 *         application thread never waits for the network.</li>
 *     <li>JSON is mapped to Java records with Jackson.</li>
 *     <li>The current session's token is added as {@code Authorization: Bearer ...}.</li>
 *     <li>Any non-2xx response becomes an {@link ApiException} with the server's error
 *         {@code code}. A 401 also notifies the {@code onUnauthorized} hook, so the app can
 *         return to the login screen when a session expires.</li>
 * </ul>
 */
public class ApiClient implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI baseUri;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient http;
    private volatile Supplier<String> tokenSupplier = () -> null;
    private volatile Runnable onUnauthorized = () -> { };

    public ApiClient(ClientConfig config) {
        this.baseUri = config.httpBaseUri();
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).executor(executor).build();
    }

    /** Where the bearer token comes from (normally the SessionManager). */
    public void setTokenSupplier(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    /** Called (on a background thread) whenever an authenticated call returns 401. */
    public void setOnUnauthorized(Runnable onUnauthorized) {
        this.onUnauthorized = onUnauthorized;
    }

    // ------------------------------------------------------------ requests

    public <T> CompletableFuture<T> get(String path, Class<T> type) {
        return send(builder(path).GET(), true).thenApply(body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> get(String path, TypeReference<T> type) {
        return send(builder(path).GET(), true).thenApply(body -> json.readValue(body, type));
    }

    public <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
        return send(withJson(builder(path), body), true).thenApply(text -> json.readValue(text, type));
    }

    /** POST without an expected response body (e.g. 204 No Content). */
    public CompletableFuture<Void> post(String path, Object body) {
        return send(withJson(builder(path), body), true).thenApply(text -> null);
    }

    /** POST that never sends a token and never triggers the unauthorized hook (used for login). */
    public <T> CompletableFuture<T> postAnonymous(String path, Object body, Class<T> type) {
        return send(withJson(builder(path), body), false).thenApply(text -> json.readValue(text, type));
    }

    /** {@code PUT path} with a JSON body. */
    public <T> CompletableFuture<T> put(String path, Object body, Class<T> type) {
        String text = json.writeValueAsString(body);
        HttpRequest.Builder b = builder(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(text));
        return send(b, true).thenApply(t -> json.readValue(t, type));
    }

    /** GET on behalf of an explicit token (used by tools that act as several operators at once). */
    public <T> CompletableFuture<T> getAs(String token, String path, Class<T> type) {
        HttpRequest.Builder b = builder(path).header("Authorization", "Bearer " + token).GET();
        return send(b, false).thenApply(text -> json.readValue(text, type));
    }

    /** Empty POST with an explicit token and no unauthorized hook (used for logout). */
    public CompletableFuture<Void> postWithToken(String path, String token) {
        HttpRequest.Builder b = withJson(builder(path), null).header("Authorization", "Bearer " + token);
        return send(b, false).thenApply(text -> null);
    }

    /** POST on behalf of an explicit token (used by tools that act as several operators at once). */
    public <T> CompletableFuture<T> postAs(String token, String path, Object body, Class<T> type) {
        HttpRequest.Builder b = withJson(builder(path), body).header("Authorization", "Bearer " + token);
        return send(b, false).thenApply(text -> json.readValue(text, type));
    }

    // ------------------------------------------------------------- internals

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, application/problem+json");
    }

    private HttpRequest.Builder withJson(HttpRequest.Builder b, Object body) {
        String text = body == null ? "" : json.writeValueAsString(body);
        return b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(text));
    }

    private CompletableFuture<String> send(HttpRequest.Builder builder, boolean authenticated) {
        if (authenticated) {
            String token = tokenSupplier.get();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        throw new CompletionException(ApiException.unreachable(describe(error)));
                    }
                    if (response.statusCode() / 100 == 2) {
                        return response.body();
                    }
                    ApiException problem = toApiException(response);
                    if (authenticated && problem.isUnauthorized()) {
                        onUnauthorized.run();
                    }
                    throw new CompletionException(problem);
                });
    }

    /** Converts an error response (problem+json, or anything else) into an ApiException. */
    ApiException toApiException(HttpResponse<String> response) {
        return parseProblem(response.statusCode(), response.body());
    }

    ApiException parseProblem(int status, String body) {
        try {
            JsonNode node = json.readTree(body);
            String code = node.path("code").asString("HTTP_" + status);
            String detail = node.path("detail").asString(node.path("title").asString("HTTP " + status));
            List<ApiException.FieldError> errors = new ArrayList<>();
            for (JsonNode e : node.path("errors")) {
                errors.add(new ApiException.FieldError(e.path("field").asString(""), e.path("message").asString("")));
            }
            return new ApiException(status, code, detail, errors);
        } catch (RuntimeException notJson) {
            return new ApiException(status, "HTTP_" + status, "Server returned HTTP " + status, null);
        }
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof HttpConnectTimeoutException || cause instanceof HttpTimeoutException) {
            return "timed out";
        }
        if (cause instanceof ConnectException) {
            return "connection refused";
        }
        if (cause instanceof IOException && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return cause.getClass().getSimpleName();
    }

    /** Unwraps the ApiException from a failed future (or wraps anything unexpected). */
    public static ApiException unwrap(Throwable error) {
        Throwable t = error;
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof ApiException api ? api : new ApiException(0, "CLIENT_ERROR", String.valueOf(t.getMessage()), null);
    }

    public URI baseUri() {
        return baseUri;
    }

    public JsonMapper json() {
        return json;
    }

    @Override
    public void close() {
        http.close();
        executor.shutdownNow();
    }
}
