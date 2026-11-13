package com.smartpark.client.app;

import com.smartpark.client.print.PrintSettings;
import com.smartpark.client.print.ReceiptPrinter;
import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.service.AdminService;
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
 *   ParkingService   parking operations (slots, categories, entry, exit) on top of ApiClient
 *   AdminService     admin operations (history, slot and user management, activity)
 *   ReceiptPrinter   receipt printing (JavaFX printing API) with this PC's printer settings
 * </pre>
 */
public final class AppContext implements AutoCloseable {

    private final ClientConfig config;
    private final ApiClient api;
    private final SessionManager session;
    private final RealtimeService realtime;
    private final ParkingService parking;
    private final AdminService admin;
    private final ReceiptPrinter receiptPrinter = new ReceiptPrinter();
    private final PrintSettings printSettings = PrintSettings.from(ClientConfig.loadProperties());

    public AppContext(ClientConfig config) {
        this.config = config;
        this.api = new ApiClient(config);
        this.session = new SessionManager(api, config.terminalName());
        this.realtime = new RealtimeService(config);
        this.parking = new ParkingService(api);
        this.admin = new AdminService(api);
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

    public AdminService admin() {
        return admin;
    }

    public ReceiptPrinter receiptPrinter() {
        return receiptPrinter;
    }

    /** Printer name and paper size configured for this PC. */
    public PrintSettings printSettings() {
        return printSettings;
    }

    @Override
    public void close() {
        realtime.stop();
        session.logout().orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(e -> null).join();
        api.close();
    }
}
