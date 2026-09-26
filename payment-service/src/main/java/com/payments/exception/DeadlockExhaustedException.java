package com.payments.exception;

/** Thrown when deadlock retry budget (3 attempts) is exhausted (REQ-F-014). */
public class DeadlockExhaustedException extends RuntimeException {
    public DeadlockExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
