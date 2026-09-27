package com.payments.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when the original receiver's current balance is insufficient to support a reversal.
 * Maps to HTTP 422 INSUFFICIENT_FUNDS_FOR_REVERSAL.
 * REQ-F-025, ST-006-02
 */
public class InsufficientFundsForReversalException extends RuntimeException {

    public InsufficientFundsForReversalException(UUID receiverAccountId,
                                                  BigDecimal available,
                                                  BigDecimal required) {
        super("Receiver account " + receiverAccountId
                + " has insufficient balance for reversal (available="
                + available.toPlainString() + ", required=" + required.toPlainString() + ")");
    }
}
