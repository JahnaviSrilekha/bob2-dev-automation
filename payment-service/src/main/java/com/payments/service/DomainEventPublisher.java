package com.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events to RabbitMQ AFTER the database transaction commits.
 *
 * Uses TransactionSynchronizationManager.registerSynchronization() with an
 * afterCommit() callback to guarantee events are never published for rolled-back
 * transactions (at-least-once, post-commit — §11.4).
 *
 * If no transaction is active (e.g., test context), the event is published immediately.
 */
@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String completedRoutingKey;

    private final String reversedRoutingKey;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${app.rabbitmq.exchange:payments.topic}") String exchange,
                                @Value("${app.rabbitmq.routing-key.completed:payment.completed}") String completedRoutingKey,
                                @Value("${app.rabbitmq.routing-key.reversed:payment.reversed}") String reversedRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.completedRoutingKey = completedRoutingKey;
        this.reversedRoutingKey = reversedRoutingKey;
    }

    /**
     * Registers an afterCommit hook to publish a PaymentCompletedEvent.
     * Guaranteed not to fire if the surrounding transaction rolls back.
     */
    public void publishAfterCommit(PaymentCompletedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
        } else {
            doPublish(event);
        }
    }

    /**
     * Registers an afterCommit hook to publish a PaymentReversedEvent.
     * Guaranteed not to fire if the surrounding transaction rolls back.
     * ST-006-03
     */
    public void publishReversedAfterCommit(PaymentReversedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishReversed(event);
                }
            });
        } else {
            doPublishReversed(event);
        }
    }

    private void doPublish(PaymentCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, completedRoutingKey, event);
            log.debug("Published payment.completed event for transactionId={}", event.transactionId());
        } catch (Exception ex) {
            // Log WARN — broker unavailable at commit time; manual re-publish tooling handles this (§11.4)
            log.warn("Failed to publish payment.completed event for transactionId={}: {}",
                    event.transactionId(), ex.getMessage());
        }
    }

    private void doPublishReversed(PaymentReversedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, reversedRoutingKey, event);
            log.debug("Published payment.reversed event for reversalTransactionId={}", event.reversalTransactionId());
        } catch (Exception ex) {
            log.warn("Failed to publish payment.reversed event for reversalTransactionId={}: {}",
                    event.reversalTransactionId(), ex.getMessage());
        }
    }
}
