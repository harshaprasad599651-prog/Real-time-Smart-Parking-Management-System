package com.smartpark.server.websocket;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.event.ParkingDomainEvent;
import com.smartpark.server.websocket.RealtimePayloads.MapUpdate;
import com.smartpark.server.websocket.RealtimePayloads.SlotChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Translates committed domain events into real-time broadcasts.
 * <pre>
 * VehicleEntered     -> SLOT_OCCUPIED, then VEHICLE_ENTERED
 * VehicleExited      -> SLOT_RELEASED, then VEHICLE_EXITED
 * SlotReleased       -> SLOT_RELEASED
 * ParkingMapChanged  -> PARKING_MAP_UPDATED
 * </pre>
 * Services publish each event exactly once, after their MongoDB writes succeeded and
 * outside any transaction retry loop, so a retried transaction never produces duplicates.
 * A broadcast failure is logged and never fails the REST request that caused it.
 */
@Component
public class ParkingEventRelay {

    private static final Logger log = LoggerFactory.getLogger(ParkingEventRelay.class);

    private final RealtimeHub hub;

    public ParkingEventRelay(RealtimeHub hub) {
        this.hub = hub;
    }

    @EventListener
    public void on(ParkingDomainEvent event) {
        try {
            switch (event) {
                case ParkingDomainEvent.VehicleEntered e -> {
                    hub.broadcast(RealtimeEventType.SLOT_OCCUPIED, e.actor(), new SlotChange(e.slot(),
                            "VEHICLE_ENTRY", e.session().ticketId(), e.session().vehicleNumber()));
                    hub.broadcast(RealtimeEventType.VEHICLE_ENTERED, e.actor(), e.session());
                }
                case ParkingDomainEvent.VehicleExited e -> {
                    if (e.slot() != null) {
                        hub.broadcast(RealtimeEventType.SLOT_RELEASED, e.actor(), new SlotChange(e.slot(),
                                "VEHICLE_EXIT", e.session().ticketId(), e.session().vehicleNumber()));
                    }
                    hub.broadcast(RealtimeEventType.VEHICLE_EXITED, e.actor(), e.session());
                }
                case ParkingDomainEvent.SlotReleased e -> hub.broadcast(RealtimeEventType.SLOT_RELEASED, e.actor(),
                        new SlotChange(e.slot(), e.reason(), null, null));
                case ParkingDomainEvent.ParkingMapChanged e -> hub.broadcast(RealtimeEventType.PARKING_MAP_UPDATED,
                        e.actor(), new MapUpdate(e.reason(), e.slot(), e.categoryName()));
                // Audit entries may mention failed logins etc.: admins only, not sequenced/replayed.
                case ParkingDomainEvent.ActivityRecorded e -> hub.notifyRole(UserRole.ADMIN,
                        RealtimeEventType.ACTIVITY_RECORDED, e.actor(), e.entry());
            }
        } catch (RuntimeException ex) {
            log.error("Could not broadcast {}", event, ex);
        }
    }
}
