package com.smartpark.server.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One parking visit (one ticket).
 * <p>
 * Unique indexes:
 * <ul>
 *     <li>{@code ticketId}</li>
 *     <li>{@code vehicleNumber} <b>where status = ACTIVE</b>, so a vehicle can't be parked twice</li>
 *     <li>{@code parkingSlotCode} <b>where status = ACTIVE</b>, so a slot can't hold two active sessions</li>
 * </ul>
 * The two partial indexes enforce the business rules inside MongoDB itself, even if
 * two requests race past the application-level checks.
 */
@Document(collection = ParkingSession.COLLECTION)
public class ParkingSession {

    public static final String COLLECTION = "parkingSessions";
    public static final String F_TICKET_ID = "ticketId";
    public static final String F_VEHICLE_NUMBER = "vehicleNumber";
    public static final String F_SLOT_CODE = "parkingSlotCode";
    public static final String F_STATUS = "status";
    public static final String F_ENTRY_TIME = "entryTime";
    public static final String F_EXIT_TIME = "exitTime";
    public static final String F_EXIT_OPERATOR = "exitOperator";

    @Id
    private String id;
    private String ticketId;
    private String vehicleNumber;
    private String vehicleCategory;
    private String parkingSlotCode;
    private Instant entryTime;
    private Instant exitTime;
    private SessionStatus status;
    private String entryOperator;
    private String exitOperator;

    public ParkingSession() {
    }

    public static ParkingSession start(String ticketId, String vehicleNumber, String vehicleCategory,
                                       String parkingSlotCode, Instant entryTime, String entryOperator) {
        ParkingSession s = new ParkingSession();
        s.ticketId = ticketId;
        s.vehicleNumber = vehicleNumber;
        s.vehicleCategory = vehicleCategory;
        s.parkingSlotCode = parkingSlotCode;
        s.entryTime = entryTime;
        s.status = SessionStatus.ACTIVE;
        s.entryOperator = entryOperator;
        return s;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getVehicleCategory() { return vehicleCategory; }
    public void setVehicleCategory(String vehicleCategory) { this.vehicleCategory = vehicleCategory; }

    public String getParkingSlotCode() { return parkingSlotCode; }
    public void setParkingSlotCode(String parkingSlotCode) { this.parkingSlotCode = parkingSlotCode; }

    public Instant getEntryTime() { return entryTime; }
    public void setEntryTime(Instant entryTime) { this.entryTime = entryTime; }

    public Instant getExitTime() { return exitTime; }
    public void setExitTime(Instant exitTime) { this.exitTime = exitTime; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public String getEntryOperator() { return entryOperator; }
    public void setEntryOperator(String entryOperator) { this.entryOperator = entryOperator; }

    public String getExitOperator() { return exitOperator; }
    public void setExitOperator(String exitOperator) { this.exitOperator = exitOperator; }
}
