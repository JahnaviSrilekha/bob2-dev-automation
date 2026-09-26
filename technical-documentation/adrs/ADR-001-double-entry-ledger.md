# ADR-001: Double-Entry Bookkeeping for All Balance Mutations

**Date:** 2025-01-15  
**Status:** Accepted  
**SDD Reference:** SDD-20250115-001 §3, §8.1, §10.2

---

## Context

The Payment Service is the authoritative record of every money movement in a peer-to-peer wallet platform. The core financial invariant is that **no monetary unit may be created, destroyed, or applied twice** — regardless of concurrent activity, network retries, or partial failures (BO-001, BO-004).

Two broad accounting patterns were considered:

### Option A — Single-Entry (account balance only)

Each transfer is recorded as two `UPDATE` statements — decrement sender balance, increment receiver balance — with no separate ledger table.

**Problems:**
- No immutable audit trail: any UPDATE overwrites the previous value with no history.
- Reconciliation requires reading two separate account rows whose UPDATE timestamps may differ.
- Cannot distinguish between "lost transfer" and "never attempted" from the data alone.
- Does not satisfy finance/compliance requirements for an auditable, immutable ledger (REQ-F-015, REQ-F-016).

### Option B — Double-Entry Bookkeeping (chosen)

Every transfer creates exactly **two immutable journal entries** in a `ledger_entries` table:
- A **DEBIT** entry on the sender's account (money leaving)
- A **CREDIT** entry on the receiver's account (money arriving)

Both entries share the same `transaction_id`, carry equal `amount` values, and are written inside a **single ACID transaction** alongside the balance `UPDATE` statements. The net sum of DEBIT amounts and CREDIT amounts for any `transaction_id` must equal zero.

This is the standard accounting model used by every financial system for centuries. It provides:
- A permanent, queryable history of every balance mutation.
- A mathematically verifiable invariant: `SUM(CREDIT) - SUM(DEBIT) = 0` for every transaction.
- A basis for point-in-time balance reconstruction without relying on a mutable balance column.
- A clean reversal model: corrections are new offsetting entries referencing the original `transaction_id`, never mutations of the original rows.

---

## Decision

**We adopt double-entry bookkeeping as the foundational persistence model.** Every balance mutation writes exactly two `ledger_entries` rows (DEBIT + CREDIT) and updates the `accounts.balance` denormalised cache inside a single `@Transactional` Spring method. The application asserts net-zero before the transaction commits; if the assertion fails, the transaction rolls back and a `LedgerIntegrityException` is thrown and paged to on-call.

Specific implementation rules:

1. The `ledger_entries` table is **insert-only**. The application DB user has `INSERT` and `SELECT` privileges only — no `UPDATE` or `DELETE`. The JPA entity class carries `@org.hibernate.annotations.Immutable`; any attempt to merge or update it throws `UnsupportedOperationException` (REQ-F-016).

2. Every `LedgerEntry` row includes: `account_id`, `entry_type` (DEBIT/CREDIT), `amount` (NUMERIC(19,4)), `currency`, `transaction_id`, `initiating_user_id`, `request_timestamp` (REQ-F-015).

3. For reversal transactions, the `reversal_of_entry_id` column links each new entry to the original entry it offsets (REQ-F-027).

4. The `accounts.balance` column is a **denormalised read optimisation**. It is kept in sync with ledger entries by the same atomic transaction. Point-in-time balances are computed by summing ledger entries with a timestamp filter (used by `getBalanceAsOf()`, REQ-F-019).

5. `LedgerService.recordTransfer()` is called **within the same `@Transactional` boundary** as the balance `UPDATE`s. Rolling back the outer transaction rolls back both the balance changes and the ledger entries atomically.

---

## Consequences

### Positive

- **Financial correctness guaranteed at the DB level**: The net-zero assertion fires before commit. An off-by-one bug in amount calculation is detected immediately, not days later in reconciliation.
- **Fully auditable**: Every balance change is permanently traced to a specific request, user, and timestamp. Satisfies REQ-F-015, REQ-F-016, REQ-NF-009, REQ-NF-010.
- **Reversals are trivially safe**: New offsetting entries leave the original ledger intact, satisfying REQ-F-025 and REQ-F-026 without mutating historical data.
- **Point-in-time balance reconstruction**: Supports `GET /balance?asOf=` (REQ-F-019) without snapshots.
- **Daily reconciliation is a query**: `SELECT SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) FROM ledger_entries WHERE DATE(request_timestamp) = ?` should always return 0.

### Negative

- **Write amplification**: Every transfer writes 2 `ledger_entries` rows in addition to 2 `accounts` row UPDATEs — 4 DB writes instead of 2. At 500 TPS this means ~2000 DB writes/second. Mitigated by PostgreSQL's WAL throughput and a properly tuned HikariCP pool (REQ-NF-004).
- **`ledger_entries` table growth**: At 500 TPS the table grows ~43 million rows/day. Requires a table-partitioning strategy (range partition by `request_timestamp`) for long-term performance. Deferred to Sprint 3+ as partition-pruning can be added non-destructively.
- **Immutability enforcement complexity**: Both DB permission grants and JPA-layer guards must be maintained. If the DB user is granted UPDATE by mistake, the application-layer guard is the last line of defence.

### Neutral

- The net-zero assertion is redundant in the happy path (both amounts come from the same variable), but is retained as a defence-in-depth check against future refactoring introducing asymmetric amounts (e.g., fee splits).
- The `accounts.balance` column is strictly a cache. In theory it could be removed and balances computed purely from ledger sums; this was rejected because a live SUM query per balance inquiry would be too slow at scale (would require materialised views).
