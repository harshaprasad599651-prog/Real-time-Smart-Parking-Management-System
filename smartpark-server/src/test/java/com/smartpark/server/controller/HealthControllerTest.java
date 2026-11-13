package com.smartpark.server.controller;

import com.smartpark.server.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthControllerTest {

    @Test
    void healthReportsUpWithServerTime() {
        ResponseEntity<HealthResponse> response = new HealthController("smartpark-server", () -> true).health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().status());
        assertEquals("UP", response.getBody().database());
        assertEquals("smartpark-server", response.getBody().service());
        assertNotNull(response.getBody().serverTime());
    }

    @Test
    void healthReportsADatabaseOutageAs503() {
        ResponseEntity<HealthResponse> response = new HealthController("smartpark-server", () -> false).health();

        assertEquals(503, response.getStatusCode().value());
        assertEquals("DEGRADED", response.getBody().status());
        assertEquals("DOWN", response.getBody().database());
        assertNotNull(response.getBody().serverTime());
    }
}
