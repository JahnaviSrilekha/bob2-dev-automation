package com.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for GET /v1/transfers/{transactionId}.
 */
public record TransactionStatusResponse(
    UUID transactionId,
    String status,
    BigDecimal amount,
    String currency,
    UUID senderAccountId,
    UUID receiverAccountId,
    Instant createdAt
) {}
