package com.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transaction entity — tracks the lifecycle of a transfer.
 * Status transitions: PENDING → COMPLETED | FAILED; COMPLETED → REVERSED.
 * REQ-F-024
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "sender_account_id", nullable = false, updatable = false)
    private UUID senderAccountId;

    @Column(name = "receiver_account_id", nullable = false, updatable = false)
    private UUID receiverAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "reversal_of_transaction_id")
    private UUID reversalOfTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(UUID id, String idempotencyKey, UUID senderAccountId,
                       UUID receiverAccountId, BigDecimal amount, String currency,
                       TransactionStatus status) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getSenderAccountId() {
        return senderAccountId;
    }

    public UUID getReceiverAccountId() {
        return receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getReversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    public void setReversalOfTransactionId(UUID reversalOfTransactionId) {
        this.reversalOfTransactionId = reversalOfTransactionId;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
