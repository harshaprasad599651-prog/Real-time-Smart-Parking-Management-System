package com.smartpark.server.document;

import java.time.Instant;

/**
 * Ticket information kept with a parking session (embedded as {@code receipt} in
 * {@code parkingSessions}).
 * <p>
 * It is written in the <b>same insert</b> as the session, so a ticket exists only for a
 * successful allocation: a rejected entry creates neither. The slot's position is copied at
 * issue time, so a reprint shows exactly what the original receipt showed, even if the slot is
 * edited later. Printing only updates the counters below; it never creates a session.
 */
public class TicketReceipt {

    public static final String F_PRINT_COUNT = "receipt.printCount";
    public static final String F_FIRST_PRINTED_AT = "receipt.firstPrintedAt";
    public static final String F_LAST_PRINTED_AT = "receipt.lastPrintedAt";
    public static final String F_LAST_PRINTED_BY = "receipt.lastPrintedBy";
    public static final String F_LAST_PRINTER = "receipt.lastPrinter";

    /** Slot position when the ticket was issued. */
    private SlotType slotType;
    private int floor;
    private String row;
    private int column;

    /** How many times the receipt was printed (0 = not printed yet; above 1 = reprints). */
    private int printCount;
    private Instant firstPrintedAt;
    private Instant lastPrintedAt;
    private String lastPrintedBy;
    private String lastPrinter;

    public TicketReceipt() {
    }

    public static TicketReceipt issuedFor(ParkingSlot slot) {
        TicketReceipt r = new TicketReceipt();
        r.slotType = slot.getSlotType();
        r.floor = slot.getFloor();
        r.row = slot.getRow();
        r.column = slot.getColumn();
        return r;
    }

    public SlotType getSlotType() { return slotType; }
    public void setSlotType(SlotType slotType) { this.slotType = slotType; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    public String getRow() { return row; }
    public void setRow(String row) { this.row = row; }

    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }

    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }

    public Instant getFirstPrintedAt() { return firstPrintedAt; }
    public void setFirstPrintedAt(Instant firstPrintedAt) { this.firstPrintedAt = firstPrintedAt; }

    public Instant getLastPrintedAt() { return lastPrintedAt; }
    public void setLastPrintedAt(Instant lastPrintedAt) { this.lastPrintedAt = lastPrintedAt; }

    public String getLastPrintedBy() { return lastPrintedBy; }
    public void setLastPrintedBy(String lastPrintedBy) { this.lastPrintedBy = lastPrintedBy; }

    public String getLastPrinter() { return lastPrinter; }
    public void setLastPrinter(String lastPrinter) { this.lastPrinter = lastPrinter; }
}
