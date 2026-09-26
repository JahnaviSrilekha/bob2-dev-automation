package com.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for POST /v1/transfers.
 * ST-001a-01
 */
public record TransferResponse(
    UUID transactionId,
    String status,
    BigDecimal amount,
    String currency,
    Instant createdAt
) {}
