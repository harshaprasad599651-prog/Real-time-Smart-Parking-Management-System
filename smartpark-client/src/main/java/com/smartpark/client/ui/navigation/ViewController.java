package com.smartpark.client.ui.navigation;

/**
 * Optional lifecycle for screen controllers. The {@link Navigator} calls {@link #onShow()}
 * after the screen is displayed and {@link #onHide()} before it is replaced. Use them to
 * start or stop real-time subscriptions, timers and refreshes.
 */
public interface ViewController {

    default void onShow() {
    }

    default void onHide() {
    }
}
