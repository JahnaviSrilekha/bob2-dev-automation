package com.payments.exception;

import java.util.UUID;

/**
 * Thrown when a reversal is attempted on a transfer that is already in REVERSED status.
 * Maps to HTTP 422 TRANSFER_ALREADY_REVERSED.
 * REQ-F-026, ST-006-02
 */
public class TransferAlreadyReversedException extends RuntimeException {

    public TransferAlreadyReversedException(UUID transactionId) {
        super("Transfer " + transactionId + " has already been reversed");
    }
}
