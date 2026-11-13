package com.smartpark.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/parking/receipts/{ticketId}/prints}, sent after the printer accepted the job. */
@Schema(description = "A receipt was printed")
public record RecordPrintRequest(
        @Size(max = 120)
        @Schema(example = "EPSON TM-T20III Receipt", description = "Printer used (optional, for the activity log)")
        String printerName) {
}
