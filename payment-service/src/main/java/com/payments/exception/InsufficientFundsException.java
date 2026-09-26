package com.payments.exception;

import java.math.BigDecimal;

/** Thrown when sender balance is less than the requested amount (REQ-F-002). */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(BigDecimal balance, BigDecimal amount) {
        super("Sender balance " + balance.toPlainString() +
              " is less than requested amount " + amount.toPlainString());
    }
}
