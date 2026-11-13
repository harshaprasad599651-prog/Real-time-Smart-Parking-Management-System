package com.smartpark.server.security;

import com.smartpark.server.document.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and checks login tokens.
 * <ul>
 *     <li>A token is 256 random bits from {@link SecureRandom}, Base64url-encoded. It is only
 *         sent to the client once, at login.</li>
 *     <li>The server keeps only the <b>SHA-256 hash</b> of each token, so a memory dump
 *         does not reveal usable tokens.</li>
 *     <li>A token expires after {@code smartpark.security.token-idle-timeout} without use
 *         (sliding expiry; default 12 h, about one shift). Logout revokes it immediately.</li>
 * </ul>
 * Tokens live in memory: restarting the server signs everyone out, and the clients then
 * show the login screen again.
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Server-side state of one login. */
    public record TokenSession(String username, UserRole role, String terminal, Instant createdAt,
                               Instant lastUsedAt, Instant expiresAt) {
    }

    /** What the client receives at login. */
    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final Duration idleTimeout;
    private final Map<String, TokenSession> sessionsByHash = new ConcurrentHashMap<>();

    public AuthTokenService(@Value("${smartpark.security.token-idle-timeout:PT12H}") Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public IssuedToken issue(String username, UserRole role, String terminal) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(idleTimeout);
        sessionsByHash.put(hash(token), new TokenSession(username, role, terminal, now, now, expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    /** Returns the session if the token is known and not expired, extending its expiry. */
    public Optional<TokenSession> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = hash(token);
        Instant now = Instant.now();
        TokenSession updated = sessionsByHash.computeIfPresent(key, (k, s) -> s.expiresAt().isBefore(now) ? null
                : new TokenSession(s.username(), s.role(), s.terminal(), s.createdAt(), now, now.plus(idleTimeout)));
        return Optional.ofNullable(updated);
    }

    public boolean revoke(String token) {
        return token != null && sessionsByHash.remove(hash(token)) != null;
    }

    /** Signs a user out everywhere (e.g. after the account is deactivated). */
    public int revokeAll(String username) {
        int before = sessionsByHash.size();
        sessionsByHash.values().removeIf(s -> s.username().equals(username));
        return before - sessionsByHash.size();
    }

    public int activeTokens() {
        return sessionsByHash.size();
    }

    @Scheduled(fixedDelayString = "PT10M")
    void purgeExpired() {
        Instant now = Instant.now();
        int before = sessionsByHash.size();
        sessionsByHash.values().removeIf(s -> s.expiresAt().isBefore(now));
        if (sessionsByHash.size() < before) {
            log.info("Purged {} expired login token(s)", before - sessionsByHash.size());
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
