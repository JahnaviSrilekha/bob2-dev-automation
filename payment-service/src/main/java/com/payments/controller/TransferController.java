package com.payments.controller;

import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Handles POST /v1/transfers.
 * Idempotency gate fires before delegation to TransferService (REQ-F-007..F-011).
 * ST-001a-02
 */
@RestController
@RequestMapping("/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService,
                              IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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
}
