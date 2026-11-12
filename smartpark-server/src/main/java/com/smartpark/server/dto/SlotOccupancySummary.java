package com.smartpark.server.dto;

/** Slot counts per status, for the admin dashboard. */
public record SlotOccupancySummary(long total, long available, long occupied, long disabled) {
}
