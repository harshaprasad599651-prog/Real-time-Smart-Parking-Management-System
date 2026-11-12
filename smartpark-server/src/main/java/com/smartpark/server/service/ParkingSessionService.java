package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ExitLookupResponse;
import com.smartpark.server.dto.PageResponse;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.ParkingSlotDto;
import com.smartpark.server.dto.VehicleEntryRequest;
import com.smartpark.server.dto.VehicleExitRequest;
import com.smartpark.server.event.ParkingDomainEvent;
import com.smartpark.server.exception.NoActiveSessionException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.exception.SlotNotCompatibleException;
import com.smartpark.server.exception.SlotUnavailableException;
import com.smartpark.server.exception.SmartParkException;
import com.smartpark.server.exception.VehicleAlreadyParkedException;
import com.smartpark.server.persistence.DuplicateKeys;
import com.smartpark.server.persistence.IndexNames;
import com.smartpark.server.persistence.MongoTransactionRunner;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.util.Normalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Vehicle entry (slot allocation) and exit (slot release).
 *
 * <h2>How double allocation is prevented</h2>
 * <ol>
 *     <li><b>Atomic slot claim.</b> A single {@code findAndModify} matches the slot only if it is
 *         {@code AVAILABLE} <i>and</i> supports the category, and flips it to {@code OCCUPIED} in
 *         the same operation. MongoDB applies single-document updates atomically, so when two
 *         operators race for one slot exactly one update matches. The other gets {@code null}
 *         and is told the slot was taken.</li>
 *     <li><b>Partial unique indexes</b> on {@code parkingSessions} (ACTIVE vehicle, ACTIVE slot)
 *         reject a second active session at the database level, even if the code above had a bug.</li>
 *     <li><b>Transaction or compensation.</b> On a replica set both writes run in one
 *         transaction. On a standalone server (no transactions) a failed session insert is
 *         compensated by releasing the slot, guarded by this request's ticket id. A periodic
 *         {@link #reconcileOrphanedSlots() reconciliation} repairs the rare crash between the
 *         two writes.</li>
 * </ol>
 */
@Service
@Validated
public class ParkingSessionService {

    private static final Logger log = LoggerFactory.getLogger(ParkingSessionService.class);
    private static final Set<UserRole> ENTRY_ROLES = EnumSet.of(UserRole.ENTRY_OPERATOR, UserRole.ADMIN);
    private static final Set<UserRole> EXIT_ROLES = EnumSet.of(UserRole.EXIT_OPERATOR, UserRole.ADMIN);

    private final MongoTemplate mongoTemplate;
    private final ParkingSessionRepository sessionRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserService userService;
    private final VehicleCategoryService categoryService;
    private final TicketIdGenerator ticketIdGenerator;
    private final MongoTransactionRunner transactionRunner;
    private final ActivityLogService activityLog;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration orphanGrace;

    public ParkingSessionService(MongoTemplate mongoTemplate,
                                 ParkingSessionRepository sessionRepository,
                                 ParkingSlotRepository slotRepository,
                                 UserService userService,
                                 VehicleCategoryService categoryService,
                                 TicketIdGenerator ticketIdGenerator,
                                 MongoTransactionRunner transactionRunner,
                                 ActivityLogService activityLog,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${smartpark.parking.orphan-slot-grace:PT2M}") Duration orphanGrace) {
        this.mongoTemplate = mongoTemplate;
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.ticketIdGenerator = ticketIdGenerator;
        this.transactionRunner = transactionRunner;
        this.activityLog = activityLog;
        this.eventPublisher = eventPublisher;
        this.orphanGrace = orphanGrace;
    }

    // ------------------------------------------------------------------ entry

    /**
     * Registers an incoming vehicle into the chosen slot.
     *
     * @return the new ACTIVE session (ticket id, slot, entry time) for the receipt
     * @throws SlotUnavailableException       slot occupied, disabled, or just taken by another operator
     * @throws SlotNotCompatibleException     slot does not accept this vehicle category
     * @throws VehicleAlreadyParkedException  vehicle already has an ACTIVE session
     */
    public ParkingSessionDto startParking(@Valid @NotNull VehicleEntryRequest request) {
        User operator = userService.requireOperator(request.operatorUsername(), ENTRY_ROLES);
        String vehicle = Normalizer.vehicleNumber(request.vehicleNumber());
        String category = categoryService.requireCategory(request.categoryName()).getCategoryName();
        String slotCode = Normalizer.slotCode(request.slotCode());

        try {
            // Fast, friendly pre-check. The partial unique index is the real guarantee.
            if (sessionRepository.existsByVehicleNumberAndStatus(vehicle, SessionStatus.ACTIVE)) {
                throw new VehicleAlreadyParkedException("Vehicle " + vehicle + " is already parked");
            }
            Instant now = now();
            String ticketId = ticketIdGenerator.next(now); // outside any transaction: avoids conflicts on the counter

            Allocation allocation = transactionRunner.execute(inTransaction ->
                    allocate(vehicle, category, slotCode, ticketId, now, operator.getUsername(), inTransaction));

            // Committed: tell everyone (WebSocket broadcast happens in ParkingEventRelay).
            ParkingSessionDto session = ParkingSessionDto.from(allocation.session());
            eventPublisher.publishEvent(new ParkingDomainEvent.VehicleEntered(
                    session, ParkingSlotDto.from(allocation.slot()), operator.getUsername()));
            activityLog.record(operator.getUsername(), ActivityAction.VEHICLE_ENTRY,
                    "Vehicle " + vehicle + " (" + category + ") parked at " + slotCode + ", ticket " + ticketId);
            return session;
        } catch (SmartParkException e) {
            activityLog.record(operator.getUsername(), ActivityAction.ALLOCATION_REJECTED,
                    "Entry of " + vehicle + " into " + slotCode + " rejected: " + e.getMessage());
            throw e;
        }
    }

    /** Result of a successful allocation: the new session and the slot as it is now (OCCUPIED). */
    private record Allocation(ParkingSession session, ParkingSlot slot) {
    }

    private Allocation allocate(String vehicle, String category, String slotCode, String ticketId,
                                Instant now, String operator, boolean inTransaction) {
        // Step 1 - atomically claim the slot (matches only if AVAILABLE and compatible).
        Query claimable = query(where(ParkingSlot.F_SLOT_CODE).is(slotCode)
                .and(ParkingSlot.F_STATUS).is(SlotStatus.AVAILABLE)
                .and(ParkingSlot.F_SUPPORTED_CATEGORIES).is(category));
        Update claim = new Update()
                .set(ParkingSlot.F_STATUS, SlotStatus.OCCUPIED)
                .set(ParkingSlot.F_CURRENT_TICKET, ticketId)
                .set(ParkingSlot.F_STATUS_CHANGED_AT, now)
                .inc(ParkingSlot.F_VERSION, 1);
        ParkingSlot claimed = mongoTemplate.findAndModify(claimable, claim, options().returnNew(true), ParkingSlot.class);
        if (claimed == null) {
            throw explainClaimFailure(slotCode, category);
        }

        // Step 2 - create the ACTIVE session. Partial unique indexes are the final safety net.
        try {
            ParkingSession session = mongoTemplate.insert(
                    ParkingSession.start(ticketId, vehicle, category, slotCode, now, operator));
            return new Allocation(session, claimed);
        } catch (DuplicateKeyException e) {
            if (!inTransaction) {
                // No transaction to roll back: undo step 1 ourselves. No event is published:
                // other clients never saw this slot as occupied.
                boolean released = releaseSlot(slotCode, ticketId, now) != null;
                log.debug("Compensated slot claim {} for ticket {} (released={})", slotCode, ticketId, released);
            }
            if (DuplicateKeys.violates(e, IndexNames.SESSIONS_ACTIVE_VEHICLE)) {
                throw new VehicleAlreadyParkedException("Vehicle " + vehicle + " is already parked", e);
            }
            if (DuplicateKeys.violates(e, IndexNames.SESSIONS_ACTIVE_SLOT)) {
                throw new SlotUnavailableException("Slot " + slotCode + " already has an active session", e);
            }
            throw e;
        }
    }

    /** Works out why the atomic claim matched nothing, to give the operator a precise message. */
    private SmartParkException explainClaimFailure(String slotCode, String category) {
        ParkingSlot slot = mongoTemplate.findOne(query(where(ParkingSlot.F_SLOT_CODE).is(slotCode)), ParkingSlot.class);
        if (slot == null) {
            return new ResourceNotFoundException("Slot '" + slotCode + "' not found");
        }
        return switch (slot.getStatus()) {
            case OCCUPIED -> new SlotUnavailableException("Slot " + slotCode + " is already occupied");
            case DISABLED -> new SlotUnavailableException("Slot " + slotCode + " is disabled");
            case AVAILABLE -> slot.getSupportedVehicleCategories().contains(category)
                    ? new SlotUnavailableException("Slot " + slotCode + " changed while allocating; please retry")
                    : new SlotNotCompatibleException("Slot " + slotCode + " does not accept " + category
                    + " (supports " + slot.getSupportedVehicleCategories() + ")");
        };
    }

    // ------------------------------------------------------------------- exit

    /**
     * Confirms the departure of a vehicle: completes its ACTIVE session and frees the slot.
     *
     * @throws NoActiveSessionException if the vehicle is not currently parked
     */
    public ParkingSessionDto completeParking(@Valid @NotNull VehicleExitRequest request) {
        User operator = userService.requireOperator(request.operatorUsername(), EXIT_ROLES);
        String vehicle = Normalizer.vehicleNumber(request.vehicleNumber());
        Instant now = now();

        Departure departure = transactionRunner.execute(inTransaction -> {
            // Step 1 - atomically complete the ACTIVE session (only one exit can win).
            ParkingSession completed = mongoTemplate.findAndModify(
                    query(where(ParkingSession.F_VEHICLE_NUMBER).is(vehicle)
                            .and(ParkingSession.F_STATUS).is(SessionStatus.ACTIVE)),
                    new Update()
                            .set(ParkingSession.F_STATUS, SessionStatus.COMPLETED)
                            .set(ParkingSession.F_EXIT_TIME, now)
                            .set(ParkingSession.F_EXIT_OPERATOR, operator.getUsername()),
                    options().returnNew(true), ParkingSession.class);
            if (completed == null) {
                throw new NoActiveSessionException("No active parking session for vehicle " + vehicle);
            }
            // Step 2 - free the slot, but only if it is still held by this ticket.
            ParkingSlot released = releaseSlot(completed.getParkingSlotCode(), completed.getTicketId(), now);
            if (released == null) {
                log.warn("Slot {} was not held by ticket {} at exit; reconciliation will verify it",
                        completed.getParkingSlotCode(), completed.getTicketId());
            }
            return new Departure(completed, released);
        });

        ParkingSessionDto session = ParkingSessionDto.from(departure.session());
        ParkingSlot slotNow = departure.releasedSlot() != null ? departure.releasedSlot()
                : slotRepository.findBySlotCode(session.parkingSlotCode()).orElse(null);
        eventPublisher.publishEvent(new ParkingDomainEvent.VehicleExited(
                session, slotNow == null ? null : ParkingSlotDto.from(slotNow), operator.getUsername()));
        activityLog.record(operator.getUsername(), ActivityAction.VEHICLE_EXIT,
                "Vehicle " + vehicle + " left slot " + session.parkingSlotCode() + ", ticket " + session.ticketId());
        return session;
    }

    /** Result of a successful exit: the completed session and the released slot (null if it was not held). */
    private record Departure(ParkingSession session, ParkingSlot releasedSlot) {
    }

    /**
     * OCCUPIED -> AVAILABLE, only if the slot is still held by {@code ticketId}.
     *
     * @return the slot after the release, or {@code null} if it was not held by this ticket
     */
    private ParkingSlot releaseSlot(String slotCode, String ticketId, Instant now) {
        Query heldByTicket = query(where(ParkingSlot.F_SLOT_CODE).is(slotCode)
                .and(ParkingSlot.F_STATUS).is(SlotStatus.OCCUPIED)
                .and(ParkingSlot.F_CURRENT_TICKET).is(ticketId));
        Update release = new Update()
                .set(ParkingSlot.F_STATUS, SlotStatus.AVAILABLE)
                .unset(ParkingSlot.F_CURRENT_TICKET)
                .set(ParkingSlot.F_STATUS_CHANGED_AT, now)
                .inc(ParkingSlot.F_VERSION, 1);
        return mongoTemplate.findAndModify(heldByTicket, release, options().returnNew(true), ParkingSlot.class);
    }

    // ---------------------------------------------------------------- queries

    public Optional<ParkingSessionDto> findActiveSession(@NotBlank String vehicleNumber) {
        return sessionRepository.findByVehicleNumberAndStatus(Normalizer.vehicleNumber(vehicleNumber), SessionStatus.ACTIVE)
                .map(ParkingSessionDto::from);
    }

    public ParkingSessionDto getByTicket(@NotBlank String ticketId) {
        return sessionRepository.findByTicketId(ticketId.trim()).map(ParkingSessionDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket '" + ticketId + "' not found"));
    }

    public List<ParkingSessionDto> listActiveSessions() {
        return sessionRepository.findByStatusOrderByEntryTimeDesc(SessionStatus.ACTIVE).stream()
                .map(ParkingSessionDto::from).toList();
    }

    public List<ParkingSessionDto> listSessionsBetween(@NotNull Instant from, @NotNull Instant to) {
        return sessionRepository.findByEntryTimeBetweenOrderByEntryTimeDesc(from, to).stream()
                .map(ParkingSessionDto::from).toList();
    }

    /**
     * Exit-gate search: the vehicle's ACTIVE session plus its assigned slot.
     *
     * @throws NoActiveSessionException if the vehicle is not currently parked
     */
    public ExitLookupResponse lookupForExit(@NotBlank String vehicleNumber) {
        String vehicle = Normalizer.vehicleNumber(vehicleNumber);
        ParkingSession session = sessionRepository.findByVehicleNumberAndStatus(vehicle, SessionStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveSessionException("No active parking session for vehicle " + vehicle));
        ParkingSlotDto slot = slotRepository.findBySlotCode(session.getParkingSlotCode())
                .map(ParkingSlotDto::from).orElse(null);
        long minutes = Math.max(0, Duration.between(session.getEntryTime(), Instant.now()).toMinutes());
        return new ExitLookupResponse(ParkingSessionDto.from(session), slot, minutes);
    }

    /**
     * Historical records, newest entry first. All filters are optional.
     *
     * @param from inclusive lower bound on entry time
     * @param to   exclusive upper bound on entry time
     */
    public PageResponse<ParkingSessionDto> searchSessions(String vehicleNumber, SessionStatus status, String slotCode,
                                                          Instant from, Instant to,
                                                          @Min(0) int page, @Min(1) @Max(200) int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        Criteria criteria = new Criteria();
        if (vehicleNumber != null && !vehicleNumber.isBlank()) {
            criteria.and(ParkingSession.F_VEHICLE_NUMBER).is(Normalizer.vehicleNumber(vehicleNumber));
        }
        if (status != null) {
            criteria.and(ParkingSession.F_STATUS).is(status);
        }
        if (slotCode != null && !slotCode.isBlank()) {
            criteria.and(ParkingSession.F_SLOT_CODE).is(Normalizer.slotCode(slotCode));
        }
        if (from != null || to != null) {
            Criteria entry = criteria.and(ParkingSession.F_ENTRY_TIME);
            if (from != null) {
                entry.gte(from);
            }
            if (to != null) {
                entry.lt(to);
            }
        }
        Query q = new Query(criteria);
        long total = mongoTemplate.count(q, ParkingSession.class);
        q.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ParkingSession.F_ENTRY_TIME)));
        List<ParkingSessionDto> content = mongoTemplate.find(q, ParkingSession.class).stream()
                .map(ParkingSessionDto::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    // --------------------------------------------------------- reconciliation

    /** Uses the configured grace period ({@code smartpark.parking.orphan-slot-grace}). */
    public int reconcileOrphanedSlots() {
        return reconcileOrphanedSlots(orphanGrace);
    }

    /**
     * Repairs slots left OCCUPIED without a matching ACTIVE session. That can only happen
     * on a standalone MongoDB if the server stops between the two steps of an entry or exit.
     * Only slots whose status has not changed for at least {@code grace} are touched, so
     * allocations still in progress are never disturbed. Each repair is a conditional
     * update on the slot's {@code version}, so it can't overwrite a concurrent change.
     *
     * @return number of slots released
     */
    public int reconcileOrphanedSlots(Duration grace) {
        Instant cutoff = Instant.now().minus(grace);
        List<ParkingSlot> suspects = mongoTemplate.find(query(where(ParkingSlot.F_STATUS).is(SlotStatus.OCCUPIED)
                .and(ParkingSlot.F_STATUS_CHANGED_AT).lt(cutoff)), ParkingSlot.class);

        int released = 0;
        for (ParkingSlot slot : suspects) {
            Optional<ParkingSession> active =
                    sessionRepository.findByParkingSlotCodeAndStatus(slot.getSlotCode(), SessionStatus.ACTIVE);
            if (active.isPresent() && Objects.equals(active.get().getTicketId(), slot.getCurrentTicketId())) {
                continue; // consistent
            }
            Query unchanged = query(where(ParkingSlot.F_SLOT_CODE).is(slot.getSlotCode())
                    .and(ParkingSlot.F_STATUS).is(SlotStatus.OCCUPIED)
                    .and(ParkingSlot.F_VERSION).is(slot.getVersion()));
            Update fix = new Update().set(ParkingSlot.F_STATUS_CHANGED_AT, now()).inc(ParkingSlot.F_VERSION, 1);
            if (active.isPresent()) {
                // Slot is legitimately occupied but points at the wrong ticket: repair the pointer.
                fix.set(ParkingSlot.F_CURRENT_TICKET, active.get().getTicketId());
                mongoTemplate.updateFirst(unchanged, fix, ParkingSlot.class);
                continue;
            }
            fix.set(ParkingSlot.F_STATUS, SlotStatus.AVAILABLE).unset(ParkingSlot.F_CURRENT_TICKET);
            ParkingSlot freed = mongoTemplate.findAndModify(unchanged, fix, options().returnNew(true), ParkingSlot.class);
            if (freed != null) {
                released++;
                eventPublisher.publishEvent(new ParkingDomainEvent.SlotReleased(ParkingSlotDto.from(freed),
                        "RECONCILIATION", ActivityLogService.SYSTEM_USER));
                activityLog.record(ActivityLogService.SYSTEM_USER, ActivityAction.SLOT_RECONCILED,
                        "Released orphaned slot " + slot.getSlotCode() + " (ticket " + slot.getCurrentTicketId() + ")");
                log.warn("Released orphaned slot {} (ticket {})", slot.getSlotCode(), slot.getCurrentTicketId());
            }
        }
        return released;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
