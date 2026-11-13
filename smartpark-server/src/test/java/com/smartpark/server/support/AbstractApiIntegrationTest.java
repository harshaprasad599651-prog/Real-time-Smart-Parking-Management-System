package com.smartpark.server.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real server (embedded Tomcat on a random port) against the test MongoDB
 * and calls it over HTTP with the JDK HttpClient, exactly as the JavaFX clients will.
 * The sample data (users, categories, 30 slots) is loaded before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Most API tests identify the operator with the X-Operator header for brevity. AuthApiTest covers
        // the real bearer-token login; OperatorHeaderDisabledTest checks the production default (false).
        properties = "smartpark.security.operator-header-enabled=true")
public abstract class AbstractApiIntegrationTest extends AbstractMongoIntegrationTest {

    protected static final String ADMIN = "admin";
    protected static final String ENTRY = "entry1";
    protected static final String EXIT = "exit1";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${local.server.port}")
    protected int port;

    @BeforeEach
    void loadSampleData() {
        seedSampleData();
    }

    // ------------------------------------------------------------ requests

    /** GET as the admin (every /api endpoint except login and health now requires a signed-in user). */
    protected Response get(String path) {
        return getAs(path, ADMIN);
    }

    /** GET identified by the X-Operator header (test mode). */
    protected Response getAs(String path, String operator) {
        return send(withOperator(request(path), operator).GET());
    }

    /** GET with no credentials at all. */
    protected Response getAnonymous(String path) {
        return send(request(path).GET());
    }

    protected Response post(String path, String operator, String json) {
        return send(withOperator(request(path), operator)
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json)));
    }

    protected Response put(String path, String operator, String json) {
        return send(withOperator(request(path), operator)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Signs in through POST /api/auth/login and returns the bearer token. */
    protected String login(String username, String password) {
        Response r = send(request("/api/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username": "%s", "password": "%s", "terminal": "TEST"}""".formatted(username, password))))
                .expect(200);
        return r.json("$.token");
    }

    protected Response getWithToken(String path, String token) {
        return send(request(path).header("Authorization", "Bearer " + token).GET());
    }

    protected Response postWithToken(String path, String token, String json) {
        return send(request(path).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Sends a request with an arbitrary Authorization header value. */
    protected Response getWithAuthorization(String path, String authorization) {
        return send(request(path).header("Authorization", authorization).GET());
    }

    protected Response enter(String vehicle, String category, String slot) {
        return post("/api/parking/entry", ENTRY, """
                {"vehicleNumber": "%s", "categoryName": "%s", "slotCode": "%s"}""".formatted(vehicle, category, slot));
    }

    protected Response leave(String vehicle) {
        return post("/api/parking/exit", EXIT, """
                {"vehicleNumber": "%s"}""".formatted(vehicle));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json");
    }

    private static HttpRequest.Builder withOperator(HttpRequest.Builder b, String operator) {
        return operator == null ? b : b.header("X-Operator", operator);
    }

    private static Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> r = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body(), r.headers().firstValue("Location"),
                    r.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ responses

    /** HTTP response with JsonPath helpers. */
    public record Response(int status, String body, Optional<String> location, String contentType) {

        public <T> T json(String jsonPath) {
            return JsonPath.read(body, jsonPath);
        }

        /** Asserts the status code, printing the body on failure. */
        public Response expect(int expectedStatus) {
            assertThat(status).as("HTTP status, body: %s", body).isEqualTo(expectedStatus);
            return this;
        }

        /** Asserts an RFC 9457 problem response with the given status and SmartPark error code. */
        public Response expectProblem(int expectedStatus, String code) {
            expect(expectedStatus);
            assertThat(contentType).startsWith("application/problem+json");
            assertThat((String) json("$.code")).isEqualTo(code);
            assertThat((Integer) json("$.status")).isEqualTo(expectedStatus);
            return this;
        }
    }
}
