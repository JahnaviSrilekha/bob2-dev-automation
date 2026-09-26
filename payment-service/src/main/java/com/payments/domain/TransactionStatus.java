package com.payments.domain;

/**
 * Transfer status state machine: PENDING → COMPLETED | FAILED; COMPLETED → REVERSED.
 * REQ-F-024
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED
}
