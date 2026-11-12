package com.smartpark.server.dto;

import com.smartpark.server.document.SlotType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Input for creating a parking slot.
 *
 * @param supportedVehicleCategories optional; if null or empty, it is derived from the
 *                                   categories whose {@code compatibleSlotTypes} contain {@code slotType}
 */
public record CreateParkingSlotRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,9}$", message = "1-10 letters, digits or '-', e.g. A-01")
        String slotCode,

        @NotNull
        SlotType slotType,

        Set<@NotBlank String> supportedVehicleCategories,

        @Min(-5) @Max(50)
        int floor,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{1,3}$", message = "1-3 letters, e.g. A")
        String row,

        @Min(1) @Max(999)
        int column) {
}
