package com.smartpark.server.dto;

import com.smartpark.server.document.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/users/{username}/role}. */
@Schema(description = "New role for an account")
public record ChangeRoleRequest(@NotNull @Schema(example = "EXIT_OPERATOR") UserRole role) {
}
