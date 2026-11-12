package com.smartpark.server.dto;

import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;

import java.time.Instant;

/** Public view of a user. The password hash is never exposed. */
public record UserDto(String id, String username, UserRole role, boolean active, Instant createdAt) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getRole(), u.isActive(), u.getCreatedAt());
    }
}
