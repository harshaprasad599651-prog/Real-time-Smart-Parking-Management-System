package com.smartpark.server.security;

import com.smartpark.server.document.LoginSession;
import com.smartpark.server.document.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
 *     <li>The server keeps only the <b>SHA-256 hash</b> of each token, in memory and in the
 *         {@code loginSessions} collection, so neither a memory dump nor the database reveals
 *         usable tokens.</li>
 *     <li>A token expires after {@code smartpark.security.token-idle-timeout} without use
 *         (sliding expiry; default 12 h, about one shift). Logout revokes it immediately.</li>
 *     <li>Because sign-ins are stored in MongoDB, a server restart does <b>not</b> sign the
 *         terminals out: they reconnect automatically and reload their data. A TTL index
 *         removes expired sign-ins.</li>
 * </ul>
 * The in-memory map is a cache in front of MongoDB; the sliding expiry is written back at most
 * about once a minute per token, so ordinary requests do not cause a database write.
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration PERSIST_INTERVAL = Duration.ofMinutes(1);

    /** Server-side state of one login. */
    public record TokenSession(String username, UserRole role, String terminal, Instant createdAt,
                               Instant lastUsedAt, Instant expiresAt) {
    }

    /** What the client receives at login. */
    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** Cached session plus the expiry last written to MongoDB. */
    private record Cached(TokenSession session, Instant persistedExpiry) {
    }

    private final Duration idleTimeout;
    private final MongoTemplate mongoTemplate;
    private final Map<String, Cached> sessionsByHash = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    @Autowired
    public AuthTokenService(@Value("${smartpark.security.token-idle-timeout:PT12H}") Duration idleTimeout,
                            MongoTemplate mongoTemplate, ApplicationEventPublisher events) {
        this.idleTimeout = idleTimeout;
        this.mongoTemplate = mongoTemplate;
        this.events = events;
    }

    /** Without event publishing (tests). */
    AuthTokenService(Duration idleTimeout, MongoTemplate mongoTemplate) {
        this(idleTimeout, mongoTemplate, event -> { });
    }

    public IssuedToken issue(String username, UserRole role, String terminal) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(idleTimeout);
        String key = hash(token);
        mongoTemplate.save(new LoginSession(key, username, role, terminal, now, now, expiresAt));
        sessionsByHash.put(key, new Cached(new TokenSession(username, role, terminal, now, now, expiresAt), expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    /** Returns the session if the token is known and not expired, extending its expiry. */
    public Optional<TokenSession> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = hash(token);
        Instant now = Instant.now();
        Cached cached = sessionsByHash.get(key);
        if (cached == null) {
            cached = load(key).orElse(null); // e.g. the first request after a server restart
            if (cached == null) {
                return Optional.empty();
            }
        }
        TokenSession s = cached.session();
        if (s.expiresAt().isBefore(now)) {
            sessionsByHash.remove(key);
            return Optional.empty();
        }
        TokenSession extended = new TokenSession(s.username(), s.role(), s.terminal(), s.createdAt(), now,
                now.plus(idleTimeout));
        Instant persisted = cached.persistedExpiry();
        if (Duration.between(persisted, extended.expiresAt()).compareTo(PERSIST_INTERVAL) > 0) {
            persisted = persistExpiry(key, extended);
        }
        sessionsByHash.put(key, new Cached(extended, persisted));
        return Optional.of(extended);
    }

    public boolean revoke(String token) {
        if (token == null) {
            return false;
        }
        String key = hash(token);
        boolean cached = sessionsByHash.remove(key) != null;
        boolean stored = mongoTemplate.remove(Query.query(Criteria.where("_id").is(key)), LoginSession.class)
                .getDeletedCount() > 0;
        return cached || stored;
    }

    /**
     * Signs a user out everywhere (e.g. after the account is deactivated or its role changed):
     * REST tokens stop working at once and open WebSocket connections are closed.
     */
    public int revokeAll(String username) {
        int before = sessionsByHash.size();
        sessionsByHash.values().removeIf(c -> c.session().username().equals(username));
        long stored = mongoTemplate.remove(Query.query(Criteria.where(LoginSession.F_USERNAME).is(username)),
                LoginSession.class).getDeletedCount();
        events.publishEvent(new SignInsRevoked(username, "Signed out by an administrator"));
        return (int) Math.max(stored, before - sessionsByHash.size());
    }

    public int activeTokens() {
        return (int) mongoTemplate.count(Query.query(Criteria.where(LoginSession.F_EXPIRES_AT).gt(Instant.now())),
                LoginSession.class);
    }

    /** Drops expired entries from the cache (MongoDB's TTL index cleans the collection). */
    @Scheduled(fixedDelayString = "PT10M")
    void purgeExpired() {
        Instant now = Instant.now();
        int before = sessionsByHash.size();
        sessionsByHash.values().removeIf(c -> c.session().expiresAt().isBefore(now));
        if (sessionsByHash.size() < before) {
            log.info("Purged {} expired login token(s)", before - sessionsByHash.size());
        }
    }

    private Optional<Cached> load(String key) {
        LoginSession stored = mongoTemplate.findById(key, LoginSession.class);
        if (stored == null) {
            return Optional.empty();
        }
        TokenSession s = new TokenSession(stored.getUsername(), stored.getRole(), stored.getTerminal(),
                stored.getCreatedAt(), stored.getLastUsedAt(), stored.getExpiresAt());
        log.debug("Restored the sign-in of {} ({}) from the database", s.username(), s.terminal());
        return Optional.of(new Cached(s, stored.getExpiresAt()));
    }

    private Instant persistExpiry(String key, TokenSession s) {
        try {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(key)),
                    new Update().set(LoginSession.F_EXPIRES_AT, s.expiresAt()).set(LoginSession.F_LAST_USED_AT, s.lastUsedAt()),
                    LoginSession.class);
            return s.expiresAt();
        } catch (RuntimeException e) {
            log.warn("Could not store the extended expiry of a sign-in: {}", e.getMessage());
            return s.expiresAt().minus(PERSIST_INTERVAL); // retried on a later request
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
