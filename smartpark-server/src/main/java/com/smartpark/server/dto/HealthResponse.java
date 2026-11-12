package com.smartpark.server.dto;

import java.time.Instant;

/**
 * Response body of {@code GET /api/health}.
 *
 * @param status     always {@code "UP"} when the server answers
 * @param service    application name
 * @param serverTime current server time (UTC)
 */
public record HealthResponse(String status, String service, Instant serverTime) {
}
