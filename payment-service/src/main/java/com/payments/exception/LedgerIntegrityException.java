package com.payments.exception;

/**
 * Thrown when a net-zero integrity assertion fails inside LedgerService.
 * This indicates a bug in the ledger write path — maps to HTTP 500 (REQ-F-017).
 */
public class LedgerIntegrityException extends RuntimeException {
    public LedgerIntegrityException(String message) {
        super(message);
    }
}
