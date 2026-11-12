package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A physical parking slot.
 * <p>
 * Unique indexes: {@code slotCode}, and {@code (floor, row, column)} so two slots can
 * never share a map position.
 * <p>
 * Concurrency fields:
 * <ul>
 *     <li>{@code currentTicketId} – ticket of the session that occupies the slot. It is set in
 *         the same atomic update that flips the status to OCCUPIED.</li>
 *     <li>{@code statusChangedAt} – when the status last changed. Used to detect orphaned
 *         allocations after a crash.</li>
 *     <li>{@code version} – optimistic-locking counter, incremented by every update.</li>
 * </ul>
 */
@Document(collection = ParkingSlot.COLLECTION)
public class ParkingSlot {

    public static final String COLLECTION = "parkingSlots";
    public static final String F_SLOT_CODE = "slotCode";
    public static final String F_SUPPORTED_CATEGORIES = "supportedVehicleCategories";
    public static final String F_STATUS = "status";
    public static final String F_FLOOR = "floor";
    public static final String F_ROW = "row";
    public static final String F_COLUMN = "column";
    public static final String F_CURRENT_TICKET = "currentTicketId";
    public static final String F_STATUS_CHANGED_AT = "statusChangedAt";
    public static final String F_VERSION = "version";

    @Id
    private String id;
    private String slotCode;
    private SlotType slotType;
    private Set<String> supportedVehicleCategories = new LinkedHashSet<>();
    private SlotStatus status;
    private int floor;
    private String row;
    private int column;

    private String currentTicketId;
    private Instant statusChangedAt;
    @Version
    private Long version;

    public ParkingSlot() {
    }

    public ParkingSlot(String slotCode, SlotType slotType, Set<String> supportedVehicleCategories,
                       SlotStatus status, int floor, String row, int column, Instant statusChangedAt) {
        this.slotCode = slotCode;
        this.slotType = slotType;
        this.supportedVehicleCategories = new LinkedHashSet<>(supportedVehicleCategories);
        this.status = status;
        this.floor = floor;
        this.row = row;
        this.column = column;
        this.statusChangedAt = statusChangedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }

    public SlotType getSlotType() { return slotType; }
    public void setSlotType(SlotType slotType) { this.slotType = slotType; }

    public Set<String> getSupportedVehicleCategories() { return supportedVehicleCategories; }
    public void setSupportedVehicleCategories(Set<String> supportedVehicleCategories) { this.supportedVehicleCategories = supportedVehicleCategories; }

    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    public String getRow() { return row; }
    public void setRow(String row) { this.row = row; }

    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }

    public String getCurrentTicketId() { return currentTicketId; }
    public void setCurrentTicketId(String currentTicketId) { this.currentTicketId = currentTicketId; }

    public Instant getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
