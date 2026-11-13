package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One signed-in terminal. Stored so that sign-ins survive a server restart: the clients simply
 * reconnect instead of returning to the login screen.
 * <p>
 * The id is the <b>SHA-256 hash</b> of the bearer token; the token itself is never stored, so
 * reading the database does not give usable tokens. A TTL index on {@code expiresAt} makes
 * MongoDB delete expired sessions automatically.
 */
@Document(collection = LoginSession.COLLECTION)
public class LoginSession {

    public static final String COLLECTION = "loginSessions";
    public static final String F_USERNAME = "username";
    public static final String F_EXPIRES_AT = "expiresAt";
    public static final String F_LAST_USED_AT = "lastUsedAt";

    @Id
    private String tokenHash;
    private String username;
    private UserRole role;
    private String terminal;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;

    public LoginSession() {
    }

    public LoginSession(String tokenHash, String username, UserRole role, String terminal,
                        Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.role = role;
        this.terminal = terminal;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
    public String getTerminal() { return terminal; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
