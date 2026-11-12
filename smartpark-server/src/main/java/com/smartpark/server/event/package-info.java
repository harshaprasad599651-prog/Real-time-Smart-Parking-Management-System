/**
 * Domain events published by the services <b>after</b> a change has been committed.
 * <p>
 * The services know nothing about WebSocket. They publish these events through Spring's
 * {@code ApplicationEventPublisher}, and {@code com.smartpark.server.websocket.ParkingEventRelay}
 * turns them into real-time messages for all connected clients.
 */
package com.smartpark.server.event;
