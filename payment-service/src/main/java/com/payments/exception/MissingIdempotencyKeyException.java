package com.payments.exception;

/** Thrown when the Idempotency-Key header is absent (REQ-F-007). */
public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException() {
        super("Idempotency-Key header is required");
    }
}
