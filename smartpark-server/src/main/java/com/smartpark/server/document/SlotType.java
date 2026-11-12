package com.smartpark.server.document;

/**
 * Physical kind of a parking slot.
 * <p>
 * A {@link VehicleCategory} lists the slot types it is compatible with; when a slot is
 * created without an explicit list of supported categories, that list is derived from
 * this mapping.
 */
public enum SlotType {
    STANDARD,
    LARGE,
    MOTORCYCLE,
    EV_CHARGING,
    ACCESSIBLE
}
