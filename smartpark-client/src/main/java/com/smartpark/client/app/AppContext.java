package com.smartpark.client.app;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.service.ParkingService;
import com.smartpark.client.service.RealtimeService;
import com.smartpark.client.session.SessionManager;

/**
 * The application's shared services, created once at start-up and handed to every
 * controller (see the controller factory in {@code Navigator}).
 * <pre>
 *   ClientConfig     server address and terminal name
 *   ApiClient        REST calls (Java HttpClient + Jackson), adds the bearer token
 *   SessionManager   login / logout / current user
 *   RealtimeService  WebSocket connection, events and connection status for the UI
 *   ParkingService   parking operations (slots, categories, entry) on top of ApiClient
 * </pre>
 */
public final class AppContext implements AutoCloseable {

    private final ClientConfig config;
    private final ApiClient api;
    private final SessionManager session;
    private final RealtimeService realtime;
    private final ParkingService parking;

    public AppContext(ClientConfig config) {
        this.config = config;
        this.api = new ApiClient(config);
        this.session = new SessionManager(api, config.terminalName());
        this.realtime = new RealtimeService(config);
        this.parking = new ParkingService(api);
    }

    public ClientConfig config() {
        return config;
    }

    public ApiClient api() {
        return api;
    }

    public SessionManager session() {
        return session;
    }

    public RealtimeService realtime() {
        return realtime;
    }

    public ParkingService parking() {
        return parking;
    }

    @Override
    public void close() {
        realtime.stop();
        session.logout().orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(e -> null).join();
        api.close();
    }
}
