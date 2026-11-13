package com.smartpark.client.ui.common;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.ApiClient;
import com.smartpark.client.net.ApiException;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.service.RealtimeService.Subscription;
import com.smartpark.client.ui.navigation.ViewController;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Base class for screens that show server data and keep it current. This is the
 * <b>REST + WebSocket pattern</b> used by every screen:
 * <ol>
 *     <li>{@link #onShow()} loads the data through REST ({@link #refresh()});</li>
 *     <li>while visible, real-time events arrive in {@link #onEvent(RealtimeEvent)}; a screen
 *         either applies the event directly or calls {@link #refreshSoon()};</li>
 *     <li>{@link #refreshSoon()} is debounced: a burst of events within 300 ms causes
 *         only one reload;</li>
 *     <li>after a reconnect that requires a resync, the screen reloads automatically;</li>
 *     <li>{@link #onHide()} cancels all subscriptions, so a closed screen does no work.</li>
 * </ol>
 */
public abstract class LiveView implements ViewController {

    protected final AppContext context;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(300));
    private boolean visible;

    protected LiveView(AppContext context) {
        this.context = context;
        debounce.setOnFinished(e -> {
            if (visible) {
                refresh();
            }
        });
    }

    @Override
    public void onShow() {
        visible = true;
        subscriptions.add(context.realtime().subscribe(this::onEvent));
        subscriptions.add(context.realtime().onResync(reason -> refresh()));
        refresh();
    }

    @Override
    public void onHide() {
        visible = false;
        subscriptions.forEach(Subscription::cancel);
        subscriptions.clear();
        debounce.stop();
    }

    /** Reloads the screen's data through REST. */
    protected abstract void refresh();

    /** A real-time event arrived (FX thread). Default: nothing. */
    protected void onEvent(RealtimeEvent event) {
    }

    protected void refreshSoon() {
        debounce.playFromStart();
    }

    protected boolean isVisible() {
        return visible;
    }

    /**
     * Runs an async API call and applies the result on the FX thread, unless the screen has been
     * closed in the meantime. Errors are shown in {@code errorLabel}. A 401 is handled globally
     * (back to login), so it is not shown here.
     */
    protected <T> void load(CompletableFuture<T> call, Consumer<T> onSuccess, Label errorLabel) {
        call.whenComplete((result, error) -> Platform.runLater(() -> {
            if (!visible) {
                return;
            }
            if (error == null) {
                hide(errorLabel);
                onSuccess.accept(result);
                return;
            }
            ApiException e = ApiClient.unwrap(error);
            if (!e.isUnauthorized() && errorLabel != null) {
                errorLabel.setText(e.code().equals(ApiException.SERVER_UNREACHABLE)
                        ? "Server unreachable - showing the last known data. Retrying when the connection returns."
                        : "Could not load data: " + e.userMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        }));
    }

    /**
     * Scrolls the page containing {@code node} back to the top. JavaFX's ScrollPane does not follow
     * focus, so after a card appears or disappears the operator could otherwise be left looking
     * at the bottom of the page.
     */
    protected static void scrollToTop(javafx.scene.Node node) {
        for (javafx.scene.Node n = node.getParent(); n != null; n = n.getParent()) {
            if (n instanceof javafx.scene.control.ScrollPane scroll) {
                Platform.runLater(() -> scroll.setVvalue(0)); // after the layout pass
                return;
            }
        }
    }

    protected static void hide(Label label) {
        if (label != null) {
            label.setVisible(false);
            label.setManaged(false);
        }
    }

    protected static boolean isParkingEvent(RealtimeEvent e) {
        return switch (e.type()) {
            case "VEHICLE_ENTERED", "VEHICLE_EXITED", "SLOT_OCCUPIED", "SLOT_RELEASED", "PARKING_MAP_UPDATED" -> true;
            default -> false;
        };
    }
}
