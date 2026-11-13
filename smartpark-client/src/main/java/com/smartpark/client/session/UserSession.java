package com.smartpark.client.session;

import java.time.Instant;

/** The signed-in user on this terminal. The token is kept in memory only, never written to disk. */
public record UserSession(String token, String username, UserRole role, String terminal, Instant expiresAt) {

    @Override
    public String toString() {
        return "UserSession[" + username + ", " + role + ", " + terminal + "]"; // never print the token
    }
}
