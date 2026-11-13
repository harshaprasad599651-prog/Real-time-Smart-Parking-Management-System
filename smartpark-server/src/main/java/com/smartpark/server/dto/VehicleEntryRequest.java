package com.smartpark.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for registering an incoming vehicle into a chosen slot. */
public record VehicleEntryRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{0,18}[A-Za-z0-9]$",
                message = "letters, digits, spaces or '-', e.g. WP CAB-1234")
        String vehicleNumber,

        @NotBlank @Size(max = 30)
        String categoryName,

        @NotBlank @Size(max = 10)
        String slotCode,

        @NotBlank
        String operatorUsername,

        @Size(max = 200)
        String notes) {

    /** Entry without notes. */
    public VehicleEntryRequest(String vehicleNumber, String categoryName, String slotCode, String operatorUsername) {
        this(vehicleNumber, categoryName, slotCode, operatorUsername, null);
    }
}
