package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.VehicleCategory;
import com.smartpark.server.dto.CreateVehicleCategoryRequest;
import com.smartpark.server.dto.VehicleCategoryDto;
import com.smartpark.server.exception.DuplicateResourceException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.repository.VehicleCategoryRepository;
import com.smartpark.server.util.Normalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Vehicle categories (CAR, MOTORCYCLE, ...) and their compatible slot types. */
@Service
@Validated
public class VehicleCategoryService {

    private final VehicleCategoryRepository repository;
    private final ActivityLogService activityLog;

    public VehicleCategoryService(VehicleCategoryRepository repository, ActivityLogService activityLog) {
        this.repository = repository;
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
