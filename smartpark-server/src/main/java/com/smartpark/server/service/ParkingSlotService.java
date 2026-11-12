package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.dto.CreateParkingSlotRequest;
import com.smartpark.server.dto.ParkingSlotDto;
import com.smartpark.server.dto.SlotOccupancySummary;
import com.smartpark.server.dto.UpdateParkingSlotRequest;
import com.smartpark.server.exception.ConcurrentUpdateException;
import com.smartpark.server.exception.DuplicateResourceException;
import com.smartpark.server.exception.InvalidStateException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.persistence.DuplicateKeys;
import com.smartpark.server.persistence.IndexNames;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.util.Normalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.smartpark.server.event.ParkingDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Parking slot management (create, list, enable, disable).
 * <p>
 * Allocation and release of slots belong to {@link ParkingSessionService}, because they
 * must happen together with the parking session changes.
 */
@Service
@Validated
public class ParkingSlotService {

    private final ParkingSlotRepository repository;
    private final MongoTemplate mongoTemplate;
    private final VehicleCategoryService categoryService;
    private final ActivityLogService activityLog;
    private final ApplicationEventPublisher eventPublisher;

    public ParkingSlotService(ParkingSlotRepository repository, MongoTemplate mongoTemplate,
                              VehicleCategoryService categoryService, ActivityLogService activityLog,
                              ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.categoryService = categoryService;
        this.activityLog = activityLog;
        this.eventPublisher = eventPublisher;
    }

    public ParkingSlotDto createSlot(@Valid @NotNull CreateParkingSlotRequest request, @NotBlank String actor) {
        String slotCode = Normalizer.slotCode(request.slotCode());
        String row = request.row().trim().toUpperCase(Locale.ROOT);
        Set<String> categories = resolveCategories(request.slotType(), request.supportedVehicleCategories());

        if (repository.existsBySlotCode(slotCode)) {
            throw new DuplicateResourceException("Slot code '" + slotCode + "' already exists");
        }
        ParkingSlot slot = new ParkingSlot(slotCode, request.slotType(), categories, SlotStatus.AVAILABLE,
                request.floor(), row, request.column(), now());
        try {
            slot = repository.insert(slot);
        } catch (DuplicateKeyException e) {
            if (DuplicateKeys.violates(e, IndexNames.SLOTS_POSITION)) {
                throw new DuplicateResourceException("Another slot already exists at floor " + request.floor()
                        + ", row " + row + ", column " + request.column(), e);
            }
            throw new DuplicateResourceException("Slot code '" + slotCode + "' already exists", e);
        }
        activityLog.record(actor, ActivityAction.SLOT_CREATED,
                "Created slot " + slotCode + " (" + request.slotType() + ") for " + categories);
        return mapChanged("SLOT_CREATED", ParkingSlotDto.from(slot), actor);
    }

    /**
     * Replaces type, supported categories and map position of a slot.
     * <p>
     * Uses optimistic locking ({@code @Version}): if an operator allocates or releases the
     * slot between our read and our write, the write is rejected instead of silently
     * overwriting the new status.
     *
     * @throws InvalidStateException      if the slot is OCCUPIED
     * @throws ConcurrentUpdateException  if the slot changed concurrently
     * @throws DuplicateResourceException if another slot already uses the new position
     */
    public ParkingSlotDto updateSlot(@NotBlank String slotCode, @Valid @NotNull UpdateParkingSlotRequest request,
                                     @NotBlank String actor) {
        String code = Normalizer.slotCode(slotCode);
        ParkingSlot slot = repository.findBySlotCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Slot '" + code + "' not found"));
        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new InvalidStateException("Slot " + code + " is OCCUPIED; it can be edited after the vehicle leaves");
        }
        String row = request.row().trim().toUpperCase(Locale.ROOT);
        slot.setSlotType(request.slotType());
        slot.setSupportedVehicleCategories(resolveCategories(request.slotType(), request.supportedVehicleCategories()));
        slot.setFloor(request.floor());
        slot.setRow(row);
        slot.setColumn(request.column());
        try {
            slot = repository.save(slot); // version-checked replace
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException("Slot " + code + " was changed by another operator; reload and retry", e);
        } catch (DuplicateKeyException e) {
            throw new DuplicateResourceException("Another slot already exists at floor " + request.floor()
                    + ", row " + row + ", column " + request.column(), e);
        }
        activityLog.record(actor, ActivityAction.SLOT_UPDATED, "Updated slot " + code + " (" + request.slotType()
                + ", floor " + request.floor() + " " + row + request.column() + ") for " + slot.getSupportedVehicleCategories());
        return mapChanged("SLOT_UPDATED", ParkingSlotDto.from(slot), actor);
    }

    /**
     * Lists slots ordered by floor, row and column. Every filter is optional.
     *
     * @param categoryName only slots that accept this vehicle category
     * @param status       only slots in this status
     * @param floor        only slots on this floor
     */
    public List<ParkingSlotDto> searchSlots(String categoryName, SlotStatus status, Integer floor) {
        Criteria criteria = new Criteria();
        if (categoryName != null && !categoryName.isBlank()) {
            String category = categoryService.requireCategory(categoryName).getCategoryName();
            criteria.and(ParkingSlot.F_SUPPORTED_CATEGORIES).is(category);
        }
        if (status != null) {
            criteria.and(ParkingSlot.F_STATUS).is(status);
        }
        if (floor != null) {
            criteria.and(ParkingSlot.F_FLOOR).is(floor);
        }
        Query q = new Query(criteria).with(Sort.by(ParkingSlot.F_FLOOR, ParkingSlot.F_ROW, ParkingSlot.F_COLUMN));
        return mongoTemplate.find(q, ParkingSlot.class).stream().map(ParkingSlotDto::from).toList();
    }

    public List<ParkingSlotDto> listSlots() {
        return repository.findAllByOrderByFloorAscRowAscColumnAsc().stream().map(ParkingSlotDto::from).toList();
    }

    /** AVAILABLE slots that accept the given vehicle category (shown on the entry operator's map). */
    public List<ParkingSlotDto> listAvailableSlots(@NotBlank String categoryName) {
        String category = categoryService.requireCategory(categoryName).getCategoryName();
        return repository
                .findByStatusAndSupportedVehicleCategoriesContainingOrderByFloorAscRowAscColumnAsc(SlotStatus.AVAILABLE, category)
                .stream().map(ParkingSlotDto::from).toList();
    }

    public ParkingSlotDto getSlot(@NotBlank String slotCode) {
        String code = Normalizer.slotCode(slotCode);
        return repository.findBySlotCode(code).map(ParkingSlotDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Slot '" + code + "' not found"));
    }

    public SlotOccupancySummary occupancySummary() {
        long available = repository.countByStatus(SlotStatus.AVAILABLE);
        long occupied = repository.countByStatus(SlotStatus.OCCUPIED);
        long disabled = repository.countByStatus(SlotStatus.DISABLED);
        return new SlotOccupancySummary(available + occupied + disabled, available, occupied, disabled);
    }

    /** AVAILABLE -> DISABLED, atomically. An occupied slot cannot be disabled. */
    public ParkingSlotDto disableSlot(@NotBlank String slotCode, @NotBlank String actor) {
        ParkingSlotDto slot = transition(slotCode, SlotStatus.AVAILABLE, SlotStatus.DISABLED);
        activityLog.record(actor, ActivityAction.SLOT_DISABLED, "Disabled slot " + slot.slotCode());
        return mapChanged("SLOT_DISABLED", slot, actor);
    }

    /** DISABLED -> AVAILABLE, atomically. */
    public ParkingSlotDto enableSlot(@NotBlank String slotCode, @NotBlank String actor) {
        ParkingSlotDto slot = transition(slotCode, SlotStatus.DISABLED, SlotStatus.AVAILABLE);
        activityLog.record(actor, ActivityAction.SLOT_ENABLED, "Enabled slot " + slot.slotCode());
        return mapChanged("SLOT_ENABLED", slot, actor);
    }

    /**
     * Conditional single-document update: it only matches if the slot is still in
     * {@code from}, so it can never overwrite a concurrent allocation.
     */
    private ParkingSlotDto transition(String slotCode, SlotStatus from, SlotStatus to) {
        String code = Normalizer.slotCode(slotCode);
        Query q = query(where(ParkingSlot.F_SLOT_CODE).is(code).and(ParkingSlot.F_STATUS).is(from));
        Update u = new Update()
                .set(ParkingSlot.F_STATUS, to)
                .set(ParkingSlot.F_STATUS_CHANGED_AT, now())
                .inc(ParkingSlot.F_VERSION, 1);
        ParkingSlot updated = mongoTemplate.findAndModify(q, u, options().returnNew(true), ParkingSlot.class);
        if (updated == null) {
            ParkingSlot current = repository.findBySlotCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Slot '" + code + "' not found"));
            throw new InvalidStateException("Slot " + code + " is " + current.getStatus() + "; expected " + from);
        }
        return ParkingSlotDto.from(updated);
    }

    /** Explicit categories must all exist; if none are given they are derived from the slot type. */
    private Set<String> resolveCategories(SlotType slotType, Set<String> requested) {
        Set<String> categories = new TreeSet<>();
        if (requested == null || requested.isEmpty()) {
            categories.addAll(categoryService.categoryNamesCompatibleWith(slotType));
            if (categories.isEmpty()) {
                throw new InvalidStateException("No vehicle category is compatible with slot type " + slotType);
            }
        } else {
            for (String raw : requested) {
                categories.add(categoryService.requireCategory(raw).getCategoryName());
            }
        }
        return categories;
    }

    /** Announces a committed map change to all connected clients and returns the slot unchanged. */
    private ParkingSlotDto mapChanged(String reason, ParkingSlotDto slot, String actor) {
        eventPublisher.publishEvent(new ParkingDomainEvent.ParkingMapChanged(reason, slot, null, actor));
        return slot;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
