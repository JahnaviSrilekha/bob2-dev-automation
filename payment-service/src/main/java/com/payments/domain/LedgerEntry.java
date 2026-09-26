package com.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * LedgerEntry entity — immutable audit record for every money movement.
 * Annotated @Immutable so Hibernate never issues UPDATE/DELETE (REQ-F-016).
 * The merge() override below provides an application-layer guard.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6, updatable = false)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "initiating_user_id", nullable = false, updatable = false)
    private UUID initiatingUserId;

    @Column(name = "request_timestamp", nullable = false, updatable = false)
    private Instant requestTimestamp;

    @Column(name = "reversal_of_entry_id", updatable = false)
    private UUID reversalOfEntryId;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID id, UUID transactionId, UUID accountId, EntryType entryType,
                       BigDecimal amount, String currency, UUID initiatingUserId,
                       Instant requestTimestamp, UUID reversalOfEntryId) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.initiatingUserId = initiatingUserId;
        this.requestTimestamp = requestTimestamp;
        this.reversalOfEntryId = reversalOfEntryId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getInitiatingUserId() {
        return initiatingUserId;
    }

    public Instant getRequestTimestamp() {
        return requestTimestamp;
    }

    public UUID getReversalOfEntryId() {
        return reversalOfEntryId;
    }
}
