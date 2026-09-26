package com.payments.exception;

/** Thrown when sender or receiver account is not found (REQ-F-002). */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(java.util.UUID accountId) {
        super("Account not found: " + accountId);
    }
}
