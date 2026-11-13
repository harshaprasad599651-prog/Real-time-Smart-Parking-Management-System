package com.smartpark.server.exception;

import com.mongodb.MongoTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Infrastructure failures become clear problem+json answers without internals. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/parking/entry"));

    @Test
    void databaseOutagesAre503() {
        ProblemDetail a = handler.handleDatabaseDown(new DataAccessResourceFailureException("connection refused"), request);
        ProblemDetail b = handler.handleDatabaseDown(new MongoTimeoutException("Timed out after 5000 ms"), request);
        for (ProblemDetail pd : new ProblemDetail[]{a, b}) {
            assertThat(pd.getStatus()).isEqualTo(503);
            assertThat(pd.getProperties()).containsEntry("code", "DATABASE_UNAVAILABLE");
            assertThat(pd.getDetail()).doesNotContain("refused", "5000"); // no driver internals
        }
    }

    @Test
    void duplicateKeysThatSlipPastTheServicesAre409() {
        ProblemDetail pd = handler.handleDuplicateKey(new DuplicateKeyException("E11000 duplicate key ..."), request);
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getProperties()).containsEntry("code", "DUPLICATE_RESOURCE");
        assertThat(pd.getDetail()).doesNotContain("E11000");
    }

    @Test
    void lockedSignInsAre429WithRetryAfter() {
        ResponseEntity<ProblemDetail> r = handler.handleTooManyLogins(
                new TooManyLoginAttemptsException(Duration.ofSeconds(90)), request);
        assertThat(r.getStatusCode().value()).isEqualTo(429);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("90");
        assertThat(r.getBody().getProperties()).containsEntry("retryAfterSeconds", 90L);
    }

    @Test
    void unexpectedErrorsHideTheirDetails() {
        ProblemDetail pd = handler.handleUnexpected(new IllegalStateException("secret internal state"), request);
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("secret");
    }
}
