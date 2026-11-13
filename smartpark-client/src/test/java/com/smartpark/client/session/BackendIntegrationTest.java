package com.smartpark.client.session;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.net.dto.ApiDtos.ParkingStatistics;
import com.smartpark.client.net.dto.ApiDtos.RealtimeStatus;
import com.smartpark.client.net.dto.ApiDtos.UserInfo;
import com.smartpark.client.realtime.ConnectionState;
import com.smartpark.client.realtime.RealtimeClient;
import com.smartpark.client.realtime.RealtimeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the client's real service classes against a running SmartPark server.
 * <p>
 * Skipped automatically when no server is reachable. Target: {@code -Dsmartpark.server.host/port}
 * (default localhost:8080). Uses the sample accounts with their default passwords.
 */
class BackendIntegrationTest {

    private static ClientConfig config;
    private ApiClient api;
    private SessionManager sessions;
    private final List<SessionManager.SessionEvent> events = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void requireServer() {
        config = ClientConfig.load();
        boolean up;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(config.serverHost(), config.serverPort()), 1500);
            up = true;
        } catch (IOException e) {
            up = false;
        }
        assumeTrue(up, "SmartPark server not running at " + config.serverHost() + ":" + config.serverPort());
    }

    @BeforeEach
    void setUp() {
        api = new ApiClient(config);
        sessions = new SessionManager(api, "TEST-TERMINAL");
        sessions.addListener((event, session) -> events.add(event));
    }

    @AfterEach
    void tearDown() {
        sessions.logout().join();
        api.close();
    }

    @Test
    void eachRoleCanSignIn() {
        String[][] accounts = {{"admin", "Admin@123", "ADMIN"}, {"entry1", "Entry@123", "ENTRY_OPERATOR"},
                {"exit1", "Exit@123", "EXIT_OPERATOR"}};
        for (String[] a : accounts) {
            UserSession s = sessions.login(a[0], a[1]).join();
            assertEquals(a[0], s.username());
            assertEquals(UserRole.valueOf(a[2]), s.role());
            assertEquals("TEST-TERMINAL", s.terminal());
            UserInfo me = sessions.verify().join();
            assertEquals(a[0], me.username());
            sessions.logout().join();
        }
        assertEquals(List.of(SessionManager.SessionEvent.SIGNED_IN, SessionManager.SessionEvent.SIGNED_OUT,
                SessionManager.SessionEvent.SIGNED_IN, SessionManager.SessionEvent.SIGNED_OUT,
                SessionManager.SessionEvent.SIGNED_IN, SessionManager.SessionEvent.SIGNED_OUT), events);
    }

    @Test
    void wrongPasswordIsRejected() {
        ApiException e = ApiClient.unwrap(assertThrows(Exception.class, () -> sessions.login("admin", "wrong").join()));
        assertEquals(401, e.status());
        assertEquals("INVALID_CREDENTIALS", e.code());
        assertTrue(sessions.current().isEmpty());
    }

    @Test
    void revokedTokenExpiresTheSession() {
        UserSession s = sessions.login("entry1", "Entry@123").join();
        // Revoke the token behind the session manager's back (as a server restart would).
        api.postWithToken("/api/auth/logout", s.token()).join();

        ApiException e = ApiClient.unwrap(assertThrows(Exception.class, () -> sessions.verify().join()));

        assertEquals("UNAUTHENTICATED", e.code());
        assertTrue(sessions.current().isEmpty());
        assertEquals(SessionManager.SessionEvent.EXPIRED, events.getLast());
    }

    @Test
    void authenticatedRestCallsAndWebSocketWork() throws Exception {
        UserSession s = sessions.login("admin", "Admin@123").join();
        ParkingStatistics stats = api.get("/api/statistics", ParkingStatistics.class).join();
        assertTrue(stats.totalSlots() > 0);

        List<ConnectionState> states = new CopyOnWriteArrayList<>();
        try (RealtimeClient ws = new RealtimeClient(config.webSocketUri(), s.token(), s.username(), "TEST-TERMINAL")) {
            ws.addListener(new RealtimeListener() {
                @Override
                public void onStateChanged(ConnectionState state, String detail) {
                    states.add(state);
                }
            });
            ws.connect();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (ws.state() != ConnectionState.CONNECTED && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(ConnectionState.CONNECTED, ws.state());

            RealtimeStatus status = api.get("/api/realtime/status", RealtimeStatus.class).join();
            assertTrue(status.clients().stream().anyMatch(c -> "TEST-TERMINAL".equals(c.terminal())
                    && "admin".equals(c.operator())));
        }
    }

    @Test
    void webSocketWithRevokedTokenStopsAsUnauthorized() throws Exception {
        UserSession s = sessions.login("exit1", "Exit@123").join();
        api.postWithToken("/api/auth/logout", s.token()).join();

        try (RealtimeClient ws = new RealtimeClient(config.webSocketUri(), s.token(), s.username(), "TEST-TERMINAL")) {
            ws.connect();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (ws.state() != ConnectionState.UNAUTHORIZED && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(ConnectionState.UNAUTHORIZED, ws.state()); // no endless reconnect loop
        }
    }
}
