package com.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for GET /v1/accounts/{accountId}/balance.
 * asOf is null for current-balance queries.
 */
public record BalanceResponse(
    UUID accountId,
    BigDecimal balance,
    String currency,
    Instant asOf
) {}
