package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A system user (administrator or gate operator).
 * Unique index: {@code username}.
 */
@Document(collection = User.COLLECTION)
public class User {

    public static final String COLLECTION = "users";
    public static final String F_USERNAME = "username";

    @Id
    private String id;
    private String username;
    private String passwordHash;
    private UserRole role;
    private boolean active;
    private Instant createdAt;

    public User() {
    }

    public User(String username, String passwordHash, UserRole role, boolean active, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
