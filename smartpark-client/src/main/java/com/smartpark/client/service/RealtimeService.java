package com.smartpark.client.service;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.realtime.ConnectionAck;
import com.smartpark.client.realtime.ConnectionState;
import com.smartpark.client.realtime.RealtimeClient;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.realtime.RealtimeListener;
import com.smartpark.client.session.UserSession;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * JavaFX-friendly wrapper around {@link RealtimeClient}.
 * <ul>
 *     <li>Exposes the connection state as observable properties, so status indicators can simply bind to it.</li>
 *     <li>Delivers events and resync requests on the <b>JavaFX application thread</b>, so subscribers can
 *         update controls directly.</li>
 *     <li>Screens subscribe in {@code onShow()} and unsubscribe in {@code onHide()} through the returned
 *         {@link Subscription}, so a closed screen never receives events.</li>
 * </ul>
 */
public final class RealtimeService {

    /** Cancels a subscription. */
    @FunctionalInterface
    public interface Subscription {
        void cancel();
    }

    private final ClientConfig config;
    private final ObjectProperty<ConnectionState> state = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
    private final StringProperty detail = new SimpleStringProperty("Not connected");
    private final ObjectProperty<Instant> lastEventAt = new SimpleObjectProperty<>();
    private final List<Consumer<RealtimeEvent>> eventHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> resyncHandlers = new CopyOnWriteArrayList<>();
    private volatile RealtimeClient client;
    private volatile Runnable onUnauthorized = () -> { };

    public RealtimeService(ClientConfig config) {
        this.config = config;
    }

    /** Opens the WebSocket for the signed-in user (closes any previous connection). */
    public void start(UserSession session) {
        stop();
        RealtimeClient c = new RealtimeClient(config.webSocketUri(), session.token(), session.username(), session.terminal());
        c.addListener(new RealtimeListener() {
            @Override
            public void onStateChanged(ConnectionState newState, String text) {
                Platform.runLater(() -> {
                    state.set(newState);
                    detail.set(text);
                    if (newState == ConnectionState.UNAUTHORIZED) {
                        onUnauthorized.run();
                    }
                });
            }

            private boolean connectedBefore;

            @Override
            public void onConnected(ConnectionAck ack) {
                boolean reconnected = connectedBefore;
                connectedBefore = true;
                Platform.runLater(() -> {
                    detail.set("Connected to " + config.serverHost() + ":" + config.serverPort()
                            + (ack.replayedEvents() > 0 ? " (" + ack.replayedEvents() + " missed updates recovered)" : ""));
                    // After any reconnect (network drop, server restart) every open screen reloads its
                    // data from the server, the single source of truth. A required resync is announced
                    // separately by onResyncRequired, so it is not triggered twice.
                    if (reconnected && !ack.resyncRequired()) {
                        resyncHandlers.forEach(h -> h.accept("reconnected"));
                    }
                });
            }

            @Override
            public void onResyncRequired(String reason) {
                Platform.runLater(() -> resyncHandlers.forEach(h -> h.accept(reason)));
            }

            @Override
            public void onEvent(RealtimeEvent event) {
                Platform.runLater(() -> {
                    lastEventAt.set(event.timestamp());
                    eventHandlers.forEach(h -> h.accept(event));
                });
            }
        });
        client = c;
        c.connect();
    }

    /** Closes the connection (e.g. at logout). Safe to call when not connected. */
    public void stop() {
        RealtimeClient c = client;
        client = null;
        if (c != null) {
            Thread.ofVirtual().start(c::close); // close() waits briefly for the close handshake
        }
        runOnFx(() -> {
            state.set(ConnectionState.DISCONNECTED);
            detail.set("Not connected");
        });
    }

    /** Receive every broadcast event on the FX thread. */
    public Subscription subscribe(Consumer<RealtimeEvent> handler) {
        eventHandlers.add(handler);
        return () -> eventHandlers.remove(handler);
    }

    /** Called when local data may be stale and should be reloaded through REST. */
    public Subscription onResync(Consumer<String> handler) {
        resyncHandlers.add(handler);
        return () -> resyncHandlers.remove(handler);
    }

    /** Called on the FX thread when the server refuses the session (expired token). */
    public void setOnUnauthorized(Runnable onUnauthorized) {
        this.onUnauthorized = onUnauthorized;
    }

    public ReadOnlyObjectProperty<ConnectionState> stateProperty() {
        return state;
    }

    public ReadOnlyStringProperty detailProperty() {
        return detail;
    }

    public ReadOnlyObjectProperty<Instant> lastEventAtProperty() {
        return lastEventAt;
    }

    private static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
