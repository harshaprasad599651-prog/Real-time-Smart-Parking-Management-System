package com.smartpark.server.dto;

import com.smartpark.server.document.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for creating a user account. The password is hashed with BCrypt and never stored. */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String username,

        // BCrypt only uses the first 72 bytes of a password
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$", message = "must contain at least one letter and one digit")
        String password,

        @NotNull
        UserRole role) {

    @Override
    public String toString() {
        // never print the password
        return "CreateUserRequest[username=" + username + ", role=" + role + "]";
    }
}
