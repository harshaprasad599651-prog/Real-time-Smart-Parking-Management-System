package com.smartpark.server.controller;

import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.ReceiptDto;
import com.smartpark.server.dto.RecordPrintRequest;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.security.RequireRole;
import com.smartpark.server.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parking receipts. A receipt exists only for a ticket the server issued on a successful entry;
 * these endpoints read it and record prints, and never create a parking session.
 */
@RestController
@RequestMapping("/api/parking/receipts")
@RequireRole({UserRole.ENTRY_OPERATOR, UserRole.ADMIN})
@Tag(name = "Receipts", description = "Print and reprint parking receipts (ENTRY_OPERATOR or ADMIN)")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Get the receipt of a ticket",
            description = "Returns the stored ticket information (vehicle, category, slot and its position, "
                    + "server entry time, operator) and how often it was printed. Used for printing and "
                    + "for reprinting by ticket ID. Read-only.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    public ReceiptDto receipt(@Parameter(example = "SP-20261113-000001") @PathVariable String ticketId) {
        return receiptService.getReceipt(ticketId);
    }

    @PostMapping("/{ticketId}/prints")
    @Operation(summary = "Record that the receipt was printed",
            description = "Called by the client after the printer accepted the job. Increments the "
                    + "print counter (the first print is the original, later ones are reprints) and writes "
                    + "RECEIPT_PRINTED or RECEIPT_REPRINTED to the activity log. Does not create a session.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    public ReceiptDto recordPrint(@Parameter(hidden = true) AuthenticatedOperator operator,
                                  @Parameter(example = "SP-20261113-000001") @PathVariable String ticketId,
                                  @Valid @RequestBody(required = false) RecordPrintRequest request) {
        return receiptService.recordPrint(ticketId, operator.username(),
                request == null ? null : request.printerName());
    }
}
