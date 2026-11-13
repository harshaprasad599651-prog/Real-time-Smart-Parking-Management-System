package com.smartpark.client.session;

import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.dto.ApiDtos.LoginRequest;
import com.smartpark.client.net.dto.ApiDtos.LoginResponse;
import com.smartpark.client.net.dto.ApiDtos.UserInfo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Signs the user in and out and holds the current {@link UserSession}.
 * <p>
 * Contains no JavaFX code, so it can be unit-tested and used from tools. Listeners are told
 * when the user signs in, signs out, or the server ends the session (token expired or revoked).
 */
public class SessionManager {

    public enum SessionEvent { SIGNED_IN, SIGNED_OUT, EXPIRED }

    public interface Listener {
        void onSessionEvent(SessionEvent event, UserSession session);
    }

    private final ApiClient api;
    private final String terminal;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile UserSession current;

    public SessionManager(ApiClient api, String terminal) {
        this.api = api;
        this.terminal = terminal;
        api.setTokenSupplier(() -> current == null ? null : current.token());
        api.setOnUnauthorized(this::expire);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** Authenticates against {@code POST /api/auth/login}. Fails with ApiException (e.g. INVALID_CREDENTIALS). */
    public CompletableFuture<UserSession> login(String username, String password) {
        return api.postAnonymous("/api/auth/login", new LoginRequest(username.trim(), password, terminal), LoginResponse.class)
                .thenApply(r -> {
                    UserSession session = new UserSession(r.token(), r.user().username(), r.user().role(),
                            terminal, r.expiresAt());
                    current = session;
                    fire(SessionEvent.SIGNED_IN, session);
                    return session;
                });
    }

    /** Asks the server who we are ({@code GET /api/auth/me}); fails with 401 if the token is no longer valid. */
    public CompletableFuture<UserInfo> verify() {
        return api.get("/api/auth/me", UserInfo.class);
    }

    /** Revokes the token on the server (best effort) and clears the local session. */
    public CompletableFuture<Void> logout() {
        UserSession session = current;
        if (session == null) {
            return CompletableFuture.completedFuture(null);
        }
        current = null; // from now on no request uses this token, and a 401 can no longer "expire" it
        return api.postWithToken("/api/auth/logout", session.token())
                .exceptionally(e -> null)               // signing out locally must work even if the server is down
                .thenRun(() -> fire(SessionEvent.SIGNED_OUT, session));
    }

    /** Called when another channel (the WebSocket) reports that the server refused our token. */
    public void invalidate() {
        expire();
    }

    /** The server rejected our token: drop the session and tell listeners (the UI returns to login). */
    private void expire() {
        UserSession session = current;
        if (session != null) {
            current = null;
            fire(SessionEvent.EXPIRED, session);
        }
    }

    public Optional<UserSession> current() {
        return Optional.ofNullable(current);
    }

    public boolean hasRole(UserRole role) {
        UserSession s = current;
        return s != null && s.role() == role;
    }

    public String terminal() {
        return terminal;
    }

    private void fire(SessionEvent event, UserSession session) {
        listeners.forEach(l -> l.onSessionEvent(event, session));
    }
}
