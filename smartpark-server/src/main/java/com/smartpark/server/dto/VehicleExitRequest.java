package com.smartpark.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for confirming a vehicle departure. */
public record VehicleExitRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{0,18}[A-Za-z0-9]$",
                message = "letters, digits, spaces or '-', e.g. WP CAB-1234")
        String vehicleNumber,

        @NotBlank
        String operatorUsername) {
}
