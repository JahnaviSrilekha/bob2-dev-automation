package com.payments.domain;

/**
 * Idempotency key lifecycle status.
 * REQ-F-007 through REQ-F-011
 */
public enum IdempotencyStatus {
    PENDING,
    COMPLETED,
    FAILED
}
