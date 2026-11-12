package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.VehicleCategory;
import com.smartpark.server.dto.CreateVehicleCategoryRequest;
import com.smartpark.server.dto.UpdateVehicleCategoryRequest;
import com.smartpark.server.dto.VehicleCategoryDto;
import com.smartpark.server.exception.DuplicateResourceException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.persistence.MongoTransactionRunner;
import com.smartpark.server.repository.VehicleCategoryRepository;
import com.smartpark.server.util.Normalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Vehicle categories (CAR, MOTORCYCLE, ...) and their compatible slot types. */
@Service
@Validated
public class VehicleCategoryService {

    private final VehicleCategoryRepository repository;
    private final MongoTemplate mongoTemplate;
    private final MongoTransactionRunner transactionRunner;
    private final ActivityLogService activityLog;

    public VehicleCategoryService(VehicleCategoryRepository repository, MongoTemplate mongoTemplate,
                                  MongoTransactionRunner transactionRunner, ActivityLogService activityLog) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.transactionRunner = transactionRunner;
        this.activityLog = activityLog;
    }

    public VehicleCategoryDto createCategory(@Valid @NotNull CreateVehicleCategoryRequest request, @NotBlank String actor) {
        String name = Normalizer.categoryName(request.categoryName());
        if (repository.existsByCategoryName(name)) {
            throw new DuplicateResourceException("Vehicle category '" + name + "' already exists");
        }
        VehicleCategory category;
        try {
            category = repository.insert(new VehicleCategory(name, request.description(), request.compatibleSlotTypes()));
        } catch (DuplicateKeyException e) {
            throw new DuplicateResourceException("Vehicle category '" + name + "' already exists", e);
        }
        activityLog.record(actor, ActivityAction.CATEGORY_CREATED,
                "Created vehicle category " + name + " " + request.compatibleSlotTypes());
        return VehicleCategoryDto.from(category);
    }

    /**
     * Replaces the description and compatible slot types of a category, and keeps the
     * slots consistent with them in the same unit of work:
     * <ul>
     *     <li>slot types that were <b>added</b>: the category is added ({@code $addToSet}) to every slot of that type</li>
     *     <li>slot types that were <b>removed</b>: the category is removed ({@code $pull}) from every slot of that type</li>
     * </ul>
     * Runs in a transaction on a replica set. On a standalone server each step is atomic
     * and repeating the same update converges to the same state.
     */
    public VehicleCategoryDto updateCategory(@NotBlank String categoryName,
                                             @Valid @NotNull UpdateVehicleCategoryRequest request,
                                             @NotBlank String actor) {
        String name = Normalizer.categoryName(categoryName);
        EnumSet<SlotType> newTypes = VehicleCategory.toEnumSet(request.compatibleSlotTypes());

        VehicleCategory updated = transactionRunner.execute(inTransaction -> {
            VehicleCategory before = mongoTemplate.findAndModify(
                    query(where(VehicleCategory.F_CATEGORY_NAME).is(name)),
                    new Update().set(VehicleCategory.F_DESCRIPTION, request.description())
                            .set(VehicleCategory.F_COMPATIBLE_SLOT_TYPES, newTypes),
                    options().returnNew(false), VehicleCategory.class);
            if (before == null) {
                throw new ResourceNotFoundException("Vehicle category '" + name + "' not found");
            }
            EnumSet<SlotType> oldTypes = VehicleCategory.toEnumSet(before.getCompatibleSlotTypes());
            EnumSet<SlotType> added = EnumSet.copyOf(newTypes);
            added.removeAll(oldTypes);
            EnumSet<SlotType> removed = EnumSet.copyOf(oldTypes);
            removed.removeAll(newTypes);

            if (!added.isEmpty()) {
                mongoTemplate.updateMulti(query(where(ParkingSlot.F_SLOT_TYPE).in(added)),
                        new Update().addToSet(ParkingSlot.F_SUPPORTED_CATEGORIES, name).inc(ParkingSlot.F_VERSION, 1),
                        ParkingSlot.class);
            }
            if (!removed.isEmpty()) {
                mongoTemplate.updateMulti(query(where(ParkingSlot.F_SLOT_TYPE).in(removed)),
                        new Update().pull(ParkingSlot.F_SUPPORTED_CATEGORIES, name).inc(ParkingSlot.F_VERSION, 1),
                        ParkingSlot.class);
            }
            before.setDescription(request.description());
            before.setCompatibleSlotTypes(newTypes);
            return before;
        });

        activityLog.record(actor, ActivityAction.CATEGORY_UPDATED,
                "Updated vehicle category " + name + " " + newTypes);
        return VehicleCategoryDto.from(updated);
    }

    public List<VehicleCategoryDto> listCategories() {
        return repository.findAllByOrderByCategoryNameAsc().stream().map(VehicleCategoryDto::from).toList();
    }

    public VehicleCategoryDto getCategory(@NotBlank String categoryName) {
        return VehicleCategoryDto.from(requireCategory(categoryName));
    }

    /** @throws ResourceNotFoundException if no category has this (normalised) name */
    public VehicleCategory requireCategory(String categoryName) {
        String name = Normalizer.categoryName(categoryName);
        return repository.findByCategoryName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category '" + name + "' not found"));
    }

    /** Names of all categories that may park in a slot of the given type. */
    public Set<String> categoryNamesCompatibleWith(@NotNull SlotType slotType) {
        return repository.findByCompatibleSlotTypesContaining(slotType).stream()
                .map(VehicleCategory::getCategoryName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
