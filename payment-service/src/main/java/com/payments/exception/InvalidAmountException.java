package com.payments.exception;

import java.math.BigDecimal;

/** Thrown when transfer amount is zero or negative (REQ-F-003). */
public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(BigDecimal amount) {
        super("Transfer amount must be positive, got: " + amount.toPlainString());
    }
}
