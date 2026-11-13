package com.smartpark.server.security;

/**
 * Published when every sign-in of a user was revoked (account deactivated, role changed).
 * The WebSocket hub listens and closes that user's live connections.
 */
public record SignInsRevoked(String username, String reason) {
}
