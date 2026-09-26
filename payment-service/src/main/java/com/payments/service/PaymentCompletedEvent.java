package com.payments.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published after a transfer commits successfully.
 * Published via DomainEventPublisher.publishAfterCommit() to RabbitMQ (§11).
 */
public record PaymentCompletedEvent(
    UUID transactionId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency,
    UUID initiatingUserId,
    Instant occurredAt
) {}
