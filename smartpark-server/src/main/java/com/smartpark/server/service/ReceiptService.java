package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.TicketReceipt;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ReceiptDto;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Parking receipts: reads the stored ticket for printing or reprinting, and records each print.
 * <p>
 * Neither operation creates a parking session or changes its status or slot. Recording a print
 * only increments the ticket's print counter in one atomic update, so a reprint can never
 * create a second session or touch a slot.
 */
@Service
public class ReceiptService {

    private static final Set<UserRole> PRINT_ROLES = EnumSet.of(UserRole.ENTRY_OPERATOR, UserRole.ADMIN);

    private final MongoTemplate mongoTemplate;
    private final ParkingSessionRepository sessionRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserService userService;
    private final ActivityLogService activityLog;

    public ReceiptService(MongoTemplate mongoTemplate, ParkingSessionRepository sessionRepository,
                          ParkingSlotRepository slotRepository, UserService userService,
                          ActivityLogService activityLog) {
        this.mongoTemplate = mongoTemplate;
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.userService = userService;
        this.activityLog = activityLog;
    }

    /** The stored ticket for {@code ticketId} (case-insensitive); 404 if it was never issued. */
    public ReceiptDto getReceipt(String ticketId) {
        return toDto(find(ticketId));
    }

    /**
     * Records that the receipt was printed. The client calls this after the printer accepted
     * the job. The first print is the original; every later one is logged as a reprint.
     */
    public ReceiptDto recordPrint(String ticketId, String username, String printerName) {
        userService.requireOperator(username, PRINT_ROLES);
        ParkingSession existing = find(ticketId);
        String printer = printerName == null || printerName.isBlank() ? null : printerName.trim();

        if (existing.getReceipt() == null) {
            // Session created before ticket information was stored: attach it once.
            TicketReceipt receipt = slotRepository.findBySlotCode(existing.getParkingSlotCode())
                    .map(TicketReceipt::issuedFor).orElseGet(TicketReceipt::new);
            mongoTemplate.updateFirst(Query.query(where(ParkingSession.F_TICKET_ID).is(existing.getTicketId())
                            .and(ParkingSession.F_RECEIPT).exists(false)),
                    new Update().set(ParkingSession.F_RECEIPT, receipt), ParkingSession.class);
        }

        Instant now = Instant.now();
        Update update = new Update()
                .inc(TicketReceipt.F_PRINT_COUNT, 1)
                .min(TicketReceipt.F_FIRST_PRINTED_AT, now)
                .set(TicketReceipt.F_LAST_PRINTED_AT, now)
                .set(TicketReceipt.F_LAST_PRINTED_BY, username)
                .set(TicketReceipt.F_LAST_PRINTER, printer);
        ParkingSession updated = mongoTemplate.findAndModify(
                Query.query(where(ParkingSession.F_TICKET_ID).is(existing.getTicketId())),
                update, options().returnNew(true), ParkingSession.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Ticket '" + ticketId + "' not found");
        }

        int count = updated.getReceipt().getPrintCount();
        boolean reprint = count > 1;
        activityLog.record(username, reprint ? ActivityAction.RECEIPT_REPRINTED : ActivityAction.RECEIPT_PRINTED,
                (reprint ? "Reprinted" : "Printed") + " receipt " + updated.getTicketId() + " for "
                        + updated.getVehicleNumber() + (reprint ? " (copy " + (count - 1) + ")" : "")
                        + (printer == null ? "" : " on " + printer));
        return toDto(updated);
    }

    private ParkingSession find(String ticketId) {
        String id = ticketId == null ? "" : ticketId.trim().toUpperCase(Locale.ROOT);
        return sessionRepository.findByTicketId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket '" + ticketId + "' not found"));
    }

    private ReceiptDto toDto(ParkingSession session) {
        return ReceiptDto.from(session, session.getReceipt() == null
                ? slotRepository.findBySlotCode(session.getParkingSlotCode()).orElse(null) : null);
    }
}
