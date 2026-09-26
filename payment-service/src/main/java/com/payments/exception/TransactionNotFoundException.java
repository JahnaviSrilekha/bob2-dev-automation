package com.payments.exception;

/** Thrown when transaction ID is not found. */
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(java.util.UUID transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
