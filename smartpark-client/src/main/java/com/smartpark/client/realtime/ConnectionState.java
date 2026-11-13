package com.smartpark.client.realtime;

/** Connection status shown to the operator (e.g. a green/amber/red indicator). */
public enum ConnectionState {
    /** Not connected and not trying (before {@code connect()}). */
    DISCONNECTED,
    /** First connection attempt in progress. */
    CONNECTING,
    /** Connected and receiving events. */
    CONNECTED,
    /** Connection lost; retrying with back-off. Operations should be blocked in the UI. */
    RECONNECTING,
    /** The server refused our credentials (token expired or revoked). No retries: sign in again. */
    UNAUTHORIZED,
    /** {@code close()} was called; no more reconnects. */
    CLOSED
}
