/**
 * Real-time client for the SmartPark server's WebSocket ({@code ws://server:8080/ws}).
 * <p>
 * {@link com.smartpark.client.realtime.RealtimeClient} keeps one connection open, sends
 * heartbeats, reconnects automatically with back-off, asks the server to replay missed
 * events, and drops duplicates using {@link com.smartpark.client.realtime.EventSequenceTracker}.
 * The application receives events through a {@link com.smartpark.client.realtime.RealtimeListener}.
 */
package com.smartpark.client.realtime;
