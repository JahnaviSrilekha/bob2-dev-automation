package com.payments.exception;

/** Thrown when a transfer with the same idempotency key is already in-progress (REQ-F-009). */
public class TransferInProgressException extends RuntimeException {
    public TransferInProgressException(String key) {
        super("Transfer with idempotency key '" + key + "' is already in progress");
    }
}
