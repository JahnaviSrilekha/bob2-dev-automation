package com.payments.exception;

import java.util.UUID;

/**
 * Thrown when a reversal is attempted on a transfer whose status is not COMPLETED
 * (e.g. PENDING or FAILED). Maps to HTTP 422 TRANSFER_NOT_REVERSIBLE.
 * REQ-F-026, ST-006-02
 */
public class TransferNotReversibleException extends RuntimeException {

    public TransferNotReversibleException(UUID transactionId, String status) {
        super("Transfer " + transactionId + " is not reversible (status=" + status + ")");
    }
}
