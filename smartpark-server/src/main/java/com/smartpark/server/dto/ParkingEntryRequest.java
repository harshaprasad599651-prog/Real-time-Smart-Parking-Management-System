package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/parking/entry}. The operator is identified by the
 * {@code X-Operator} header rather than the body.
 */
@Schema(description = "Vehicle entering the car park and the slot chosen on the map")
public record ParkingEntryRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{0,18}[A-Za-z0-9]$",
                message = "letters, digits, spaces or '-', e.g. WP CAB-1234")
        @Schema(example = "WP CAB-1234")
        String vehicleNumber,

        @NotBlank @Size(max = 30)
        @Schema(example = "CAR")
        String categoryName,

        @NotBlank @Size(max = 10)
        @Schema(example = "A-01")
        String slotCode) {

    public VehicleEntryRequest withOperator(String operatorUsername) {
        return new VehicleEntryRequest(vehicleNumber, categoryName, slotCode, operatorUsername);
    }
}
