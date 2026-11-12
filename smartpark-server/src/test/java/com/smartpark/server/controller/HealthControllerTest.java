package com.smartpark.server.controller;

import com.smartpark.server.dto.HealthResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthControllerTest {

    @Test
    void healthReportsUpWithServerTime() {
        HealthResponse response = new HealthController("smartpark-server").health();

        assertEquals("UP", response.status());
        assertEquals("smartpark-server", response.service());
        assertNotNull(response.serverTime());
    }
}
