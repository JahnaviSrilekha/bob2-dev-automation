package com.payments.controller;

import com.payments.dto.ErrorResponse;
import com.payments.dto.ReversalResponse;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.service.IdempotencyService;
import com.payments.service.ReversalService;
import com.payments.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Handles POST /v1/transfers and POST /v1/transfers/{transactionId}/reverse.
 * Idempotency gate fires before delegation to TransferService (REQ-F-007..F-011).
 * ST-001a-02, ST-006-01
 */
@Tag(name = "Transfers", description = "Initiate, query, and reverse fund transfers")
@RestController
@RequestMapping("/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final ReversalService reversalService;

    public TransferController(TransferService transferService,
                              IdempotencyService idempotencyService,
                              ReversalService reversalService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
        this.reversalService = reversalService;
    }

    @Operation(
        summary     = "Initiate a transfer",
        description = "Atomically debits the sender and credits the receiver. Requires a unique Idempotency-Key UUID header. Retrying with the same key returns the cached response without re-executing."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer completed",
            content = @Content(schema = @Schema(implementation = TransferResponse.class))),
        @ApiResponse(responseCode = "200", description = "Idempotent replay — original response returned",
            content = @Content(schema = @Schema(implementation = TransferResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid fields / missing Idempotency-Key",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Transfer with this key is still in progress",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Business rule violation (insufficient funds, self-transfer, key conflict)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Deadlock retry budget exhausted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Parameter(description = "UUID v4 idempotency key — must be unique per transfer attempt", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "Caller user ID (injected by API Gateway)", required = false)
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "") String userId,
            @Valid @RequestBody TransferRequest request) {

        UUID userUuid = userId.isBlank() ? UUID.randomUUID() : UUID.fromString(userId);

        // Idempotency gate: check-before-write (ADR-003)
        IdempotencyService.IdempotencyResult gate =
                idempotencyService.checkOrCreate(idempotencyKey, request);

        if (gate.isCached()) {
            // Idempotent replay of a completed transfer — return original response
            return ResponseEntity.ok(gate.cachedResponse());
        }

        try {
            TransferResponse response = transferService.initiateTransfer(request, userUuid, idempotencyKey);
            idempotencyService.markCompleted(idempotencyKey, response);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            idempotencyService.markFailed(idempotencyKey);
            throw ex;
        }
    }

    /**
     * Reverses a COMPLETED transfer by creating offsetting journal entries.
     * Returns HTTP 201 with the reversal transaction ID on success.
     * No idempotency gate — a repeated call on an already-REVERSED transfer
     * returns 422 TRANSFER_ALREADY_REVERSED (REQ-F-025, REQ-F-026).
     * ST-006-01
     */
    @Operation(
        summary     = "Reverse a completed transfer",
        description = "Creates offsetting journal entries (credit original sender, debit original receiver). The original transaction status becomes REVERSED. Fails if status is not COMPLETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reversal executed",
            content = @Content(schema = @Schema(implementation = ReversalResponse.class))),
        @ApiResponse(responseCode = "404", description = "Transaction not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Transfer not reversible, already reversed, or receiver has insufficient funds",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<ReversalResponse> reverseTransfer(
            @Parameter(description = "ID of the COMPLETED transaction to reverse", required = true)
            @PathVariable UUID transactionId,
            @Parameter(description = "Caller user ID (injected by API Gateway)", required = false)
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "") String userId) {

        UUID userUuid = userId.isBlank() ? UUID.randomUUID() : UUID.fromString(userId);
        ReversalResponse response = reversalService.reverse(transactionId, userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
