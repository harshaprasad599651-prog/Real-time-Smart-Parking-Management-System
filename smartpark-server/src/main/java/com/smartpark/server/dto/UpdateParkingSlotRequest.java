package com.smartpark.server.dto;

import com.smartpark.server.document.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Replaces a slot's type, supported categories and map position. The slot code and
 * status cannot be changed here: use the disable/enable endpoints for the status.
 * An OCCUPIED slot cannot be edited.
 */
@Schema(description = "New type, supported categories and map position for an existing slot")
public record UpdateParkingSlotRequest(
        @NotNull
        SlotType slotType,

        @Schema(description = "Optional. If empty, derived from the categories compatible with slotType")
        Set<@NotBlank String> supportedVehicleCategories,

        @Min(-5) @Max(50)
        int floor,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{1,3}$", message = "1-3 letters, e.g. A")
        String row,

        @Min(1) @Max(999)
        int column) {
}
