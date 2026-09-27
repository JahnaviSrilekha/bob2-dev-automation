package com.payments.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for POST /v1/transfers/{transactionId}/reverse.
 * ST-006-01
 */
public record ReversalResponse(
        UUID reversalTransactionId,
        UUID originalTransactionId,
        String status,
        Instant createdAt
) {}
