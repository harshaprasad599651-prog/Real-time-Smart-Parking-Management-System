package com.smartpark.server.dto;

import com.smartpark.server.document.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Replaces a category's description and compatible slot types. The name cannot change,
 * because slots and historical sessions refer to it.
 */
@Schema(description = "New description and compatible slot types for an existing category")
public record UpdateVehicleCategoryRequest(
        @Size(max = 200)
        @Schema(example = "Standard passenger car")
        String description,

        @NotEmpty
        @Schema(example = "[\"STANDARD\", \"LARGE\"]")
        Set<@NotNull SlotType> compatibleSlotTypes) {
}
