package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/login}. */
@Schema(description = "Credentials and the terminal the user signs in from")
public record LoginRequest(
        @NotBlank @Size(max = 30) @Schema(example = "entry1") String username,
        @NotBlank @Size(max = 72) @Schema(example = "Entry@123") String password,
        @Size(max = 40) @Schema(example = "ENTRY-GATE-1") String terminal) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", terminal=" + terminal + "]"; // never log the password
    }
}
