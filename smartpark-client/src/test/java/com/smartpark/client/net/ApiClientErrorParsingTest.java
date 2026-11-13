package com.smartpark.client.net;

import com.smartpark.client.config.ClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientErrorParsingTest {

    private final ApiClient api = new ApiClient(new ClientConfig("localhost", 1, "TEST"));

    @AfterEach
    void close() {
        api.close();
    }

    @Test
    void readsCodeDetailAndFieldErrorsFromProblemJson() {
        ApiException e = api.parseProblem(400, """
                {"status":400,"title":"Bad Request","detail":"Request validation failed","code":"VALIDATION_FAILED",
                 "errors":[{"field":"vehicleNumber","message":"must not be blank"}]}""");

        assertEquals(400, e.status());
        assertEquals("VALIDATION_FAILED", e.code());
        assertEquals("Request validation failed", e.getMessage());
        assertEquals(1, e.fieldErrors().size());
        assertEquals("vehicleNumber: must not be blank", e.userMessage());
    }

    @Test
    void unauthorizedIsRecognised() {
        ApiException e = api.parseProblem(401, """
                {"status":401,"detail":"Invalid username or password","code":"INVALID_CREDENTIALS"}""");
        assertTrue(e.isUnauthorized());
        assertEquals("INVALID_CREDENTIALS", e.code());
    }

    @Test
    void nonJsonBodyStillGivesAUsableError() {
        ApiException e = api.parseProblem(502, "<html>Bad gateway</html>");
        assertEquals("HTTP_502", e.code());
        assertEquals("Server returned HTTP 502", e.getMessage());
    }

    @Test
    void unreachableServerIsReportedAsSuch() {
        ApiException e = ApiClient.unwrap(api.get("/api/health", Object.class)
                .handle((ok, err) -> err).join());
        assertEquals(ApiException.SERVER_UNREACHABLE, e.code());
        assertEquals(0, e.status());
    }
}
