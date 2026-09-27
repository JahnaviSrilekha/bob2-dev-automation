package com.payments.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published after a transfer reversal commits successfully.
 * Published via DomainEventPublisher.publishReversedAfterCommit() to RabbitMQ (§11).
 * ST-006-03
 */
public record PaymentReversedEvent(
        UUID reversalTransactionId,
        UUID originalTransactionId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        String currency,
        UUID initiatingUserId,
        Instant occurredAt
) {}
