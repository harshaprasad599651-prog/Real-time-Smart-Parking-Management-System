package com.smartpark.server.dto;

import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.VehicleCategory;

import java.util.Set;

public record VehicleCategoryDto(String id, String categoryName, String description, Set<SlotType> compatibleSlotTypes) {

    public static VehicleCategoryDto from(VehicleCategory c) {
        return new VehicleCategoryDto(c.getId(), c.getCategoryName(), c.getDescription(),
                Set.copyOf(c.getCompatibleSlotTypes()));
    }
}
