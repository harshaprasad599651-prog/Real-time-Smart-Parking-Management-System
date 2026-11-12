package com.smartpark.server.repository;

import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.VehicleCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleCategoryRepository extends MongoRepository<VehicleCategory, String> {

    Optional<VehicleCategory> findByCategoryName(String categoryName);

    boolean existsByCategoryName(String categoryName);

    /** Categories whose {@code compatibleSlotTypes} array contains the given slot type. */
    List<VehicleCategory> findByCompatibleSlotTypesContaining(SlotType slotType);

    List<VehicleCategory> findAllByOrderByCategoryNameAsc();
}
