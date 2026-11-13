package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/parking/exit}. The operator is identified by the bearer token. */
@Schema(description = "Vehicle whose departure is confirmed")
public record ParkingExitRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{0,18}[A-Za-z0-9]$",
                message = "letters, digits, spaces or '-', e.g. WP CAB-1234")
        @Schema(example = "WP CAB-1234")
        String vehicleNumber) {

    public VehicleExitRequest withOperator(String operatorUsername) {
        return new VehicleExitRequest(vehicleNumber, operatorUsername);
    }
}
