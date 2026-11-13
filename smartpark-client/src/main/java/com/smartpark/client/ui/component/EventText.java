package com.smartpark.client.ui.component;

import com.smartpark.client.realtime.RealtimeEvent;

/** Human-readable text for real-time events, shared by all screens. */
public final class EventText {

    private EventText() {
    }

    public static String tag(RealtimeEvent e) {
        return switch (e.type()) {
            case "VEHICLE_ENTERED" -> "ENTRY";
            case "VEHICLE_EXITED" -> "EXIT";
            case "SLOT_OCCUPIED" -> "OCCUPIED";
            case "SLOT_RELEASED" -> "RELEASED";
            case "PARKING_MAP_UPDATED" -> "MAP";
            case "OPERATOR_CONNECTED" -> "ONLINE";
            case "OPERATOR_DISCONNECTED" -> "OFFLINE";
            default -> e.type();
        };
    }

    public static String tagStyle(RealtimeEvent e) {
        return switch (e.type()) {
            case "VEHICLE_ENTERED", "SLOT_OCCUPIED" -> "tag-blue";
            case "VEHICLE_EXITED", "SLOT_RELEASED", "OPERATOR_CONNECTED" -> "tag-green";
            case "OPERATOR_DISCONNECTED" -> "tag-red";
            default -> "tag-slate";
        };
    }

    public static String describe(RealtimeEvent e) {
        String by = e.actor() == null ? "" : " by " + e.actor() + (e.terminal() == null ? "" : " @ " + e.terminal());
        return switch (e.type()) {
            case "VEHICLE_ENTERED" -> e.payloadText("vehicleNumber") + " parked at " + e.payloadText("parkingSlotCode")
                    + " (" + e.payloadText("vehicleCategory") + ")" + by;
            case "VEHICLE_EXITED" -> e.payloadText("vehicleNumber") + " left " + e.payloadText("parkingSlotCode")
                    + " after " + e.payloadText("durationMinutes") + " min" + by;
            case "SLOT_OCCUPIED" -> "Slot " + e.payloadText("slot.slotCode") + " occupied by " + e.payloadText("vehicleNumber");
            case "SLOT_RELEASED" -> "Slot " + e.payloadText("slot.slotCode") + " is free again";
            case "PARKING_MAP_UPDATED" -> mapReason(e.payloadText("reason")) + " "
                    + (e.payloadText("slot.slotCode") != null ? e.payloadText("slot.slotCode")
                    : e.payloadText("categoryName") != null ? e.payloadText("categoryName") : "") + by;
            case "OPERATOR_CONNECTED" -> e.payloadText("connection.operator") + " connected from "
                    + e.payloadText("connection.terminal");
            case "OPERATOR_DISCONNECTED" -> e.payloadText("connection.operator") + " disconnected ("
                    + e.payloadText("connection.terminal") + ")";
            default -> e.type();
        };
    }

    private static String mapReason(String reason) {
        if (reason == null) {
            return "Map updated";
        }
        return switch (reason) {
            case "SLOT_CREATED" -> "Slot added:";
            case "SLOT_UPDATED" -> "Slot edited:";
            case "SLOT_DISABLED" -> "Slot disabled:";
            case "SLOT_ENABLED" -> "Slot enabled:";
            case "CATEGORY_CREATED" -> "Category added:";
            case "CATEGORY_UPDATED" -> "Category changed:";
            default -> reason;
        };
    }
}
