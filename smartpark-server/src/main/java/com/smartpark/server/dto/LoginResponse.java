package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Result of a successful login. Send {@code Authorization: Bearer <token>} with every later request. */
@Schema(description = "Access token and the signed-in user")
public record LoginResponse(
        @Schema(description = "Opaque bearer token") String token,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Expiry if unused; every request extends it") Instant expiresAt,
        UserDto user) {
}
