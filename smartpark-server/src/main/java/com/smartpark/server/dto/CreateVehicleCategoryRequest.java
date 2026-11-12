package com.smartpark.server.dto;

import com.smartpark.server.document.SlotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Input for creating a vehicle category. The name is normalised, e.g. "Electric car" becomes ELECTRIC_CAR. */
public record CreateVehicleCategoryRequest(
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9 _]*$",
                message = "must start with a letter and contain only letters, digits, spaces or '_'")
        String categoryName,

        @Size(max = 200)
        String description,

        @NotEmpty
        Set<@NotNull SlotType> compatibleSlotTypes) {
}
