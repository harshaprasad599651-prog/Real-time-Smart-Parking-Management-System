package com.smartpark.server.event;

import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.ParkingSlotDto;

/** Something that changed the car park. Published only after the change is stored in MongoDB. */
public sealed interface ParkingDomainEvent {

    /** Username of the operator who caused the change, or {@code SYSTEM}. */
    String actor();

    /** An entry operator parked a vehicle: the slot became OCCUPIED and a session became ACTIVE. */
    record VehicleEntered(ParkingSessionDto session, ParkingSlotDto slot, String actor) implements ParkingDomainEvent {
    }

    /** An exit operator confirmed a departure: the session is COMPLETED and the slot is AVAILABLE again. */
    record VehicleExited(ParkingSessionDto session, ParkingSlotDto slot, String actor) implements ParkingDomainEvent {
    }

    /** A slot was released without a vehicle exit (e.g. the reconciliation job freed an orphaned slot). */
    record SlotReleased(ParkingSlotDto slot, String reason, String actor) implements ParkingDomainEvent {
    }

    /**
     * The layout or rules of the map changed (slot added, edited, disabled or enabled; category changed).
     *
     * @param slot         the changed slot, or {@code null} if several slots may have changed
     * @param categoryName the changed category, if any
     */
    record ParkingMapChanged(String reason, ParkingSlotDto slot, String categoryName, String actor)
            implements ParkingDomainEvent {
    }
}
