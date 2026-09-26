# ADR-002: Pessimistic Row-Level Locking for Balance Mutations

**Date:** 2025-01-15  
**Status:** Accepted  
**SDD Reference:** SDD-20250115-001 §3, §8.1, §8.2.1, §12.4

---

## Context

The Payment Service must guarantee that concurrent transfers involving the same account never produce negative balances, lost updates, or corrupted ledger state (BO-003, REQ-F-012, REQ-F-013). At the stated throughput of ≥ 500 TPS, multiple in-flight transfers may reference the same account row simultaneously.

The platform uses PostgreSQL 16, which supports both optimistic and pessimistic concurrency control natively.

Two approaches were evaluated:

### Option A — Optimistic Locking (version column)

Add a `version` column to `accounts`. On each `UPDATE`, include `WHERE version = :currentVersion` in the SQL. If the version has changed since the row was read, the UPDATE affects 0 rows; Spring Data JPA throws `ObjectOptimisticLockingFailureException`; the caller retries the entire transaction from scratch.

**Problems:**
- **Retry amplification**: Under high contention (e.g., a popular sender account), every concurrent transfer reads the same version and all but one must retry. With N concurrent transfers, worst-case O(N²) total attempts.
- **Balance re-read required on each retry**: The application must re-read the sender balance on every retry; if the balance was sufficient on the first attempt but not after the competing transfer committed, the retry correctly rejects — but this adds round-trips.
- **Thundering herd**: Optimistic retries are typically unbounded or require custom retry infrastructure. The SRS specifies a maximum of 3 retries (REQ-F-014), which maps cleanly onto pessimistic locking semantics but awkwardly onto optimistic semantics (the 3rd retry could be the first to see a clean version).
- **Does not prevent negative balance by itself**: Without an additional DB-level `CHECK (balance >= 0)` constraint, a mis-implemented retry could allow a negative balance to slip through a race condition.

### Option B — Pessimistic Row-Level Locking (chosen)

Acquire an explicit `SELECT … FOR UPDATE` lock on the account rows **before** reading their balances. PostgreSQL blocks the second transaction at the lock wait until the first commits or rolls back. Only one transaction at a time can hold the lock on a given row; all others queue behind it.

**This provides a strict serialisation guarantee for all mutations to the same account.**

**Deadlock prevention — lock ordering:**  
When a transfer involves two accounts (sender and receiver), both rows must be locked. If Transaction T1 locks account A then account B, and Transaction T2 locks account B then account A simultaneously, a deadlock occurs. This is prevented by **always acquiring locks in ascending account-ID order**:

```sql
SELECT * FROM accounts
WHERE id IN (:id1, :id2)
ORDER BY id
FOR UPDATE
```

Because all transactions follow the same ordering, circular wait is impossible.

---

## Decision

**We use `SELECT … FOR UPDATE` pessimistic locking with ascending account-ID lock acquisition order for all balance mutations** (transfers and reversals). Optimistic locking is not used on the `accounts` table.

Implementation specifics:

1. `AccountRepository.findByIdForUpdate(List<UUID> ids)` executes a native query: `SELECT * FROM accounts WHERE id IN (:ids) ORDER BY id FOR UPDATE`. Spring Data `@Lock(LockModeType.PESSIMISTIC_WRITE)` is used.

2. The caller (`TransferService`, `ReversalService`) **always** passes a sorted list of account IDs: `Stream.of(senderId, receiverId).sorted().collect(toList())`.

3. `SELECT FOR UPDATE` uses PostgreSQL's default lock wait (unlimited). If a deadlock is detected by PostgreSQL's deadlock detector (typically within 1 second), it throws `ERROR 40P01 (deadlock detected)`, which Spring translates to `CannotAcquireLockException`.

4. `TransferService.initiateTransfer()` is annotated with Spring Retry:
   ```java
   @Retryable(
       retryFor  = CannotAcquireLockException.class,
       maxAttempts = 3,
       backoff   = @Backoff(delay = 50, multiplier = 2.0, random = true)
   )
   ```
   Retry 1: ~50 ms delay. Retry 2: ~100 ms delay (plus jitter). After 3 failed attempts, `@Recover` throws `DeadlockExhaustedException` → HTTP 503.

5. A DB-level `CHECK (balance >= 0)` constraint on `accounts` provides a defence-in-depth guard independent of the application layer (REQ-F-013).

6. Idempotency is maintained across retries: the idempotency key record is created with status `PENDING` **before** the `@Retryable` method is entered. If the method retries, the idempotency key is already in PENDING status; the retry proceeds without re-inserting it. On success the status is updated to COMPLETED once (after the outer commit).

---

## Consequences

### Positive

- **Strict serialisation**: Concurrent transfers involving the same account are queued; impossible to produce a negative balance or lost update. Satisfies REQ-F-012.
- **No retry amplification**: Under contention, transactions queue behind the lock holder and execute one at a time. Total work is O(N), not O(N²) as with optimistic retries.
- **Deterministic deadlock prevention**: Ascending lock order eliminates circular waits; in practice deadlocks should be extremely rare (only occur when PostgreSQL's own deadlock detection is triggered by unrelated transactions).
- **REQ-F-014 compliance**: The retry budget of 3 maps cleanly onto `CannotAcquireLockException` retry semantics.
- **Simplicity**: No version columns, no stale-read handling, no complex retry policies in business logic.

### Negative

- **Latency under high contention**: If a hot account (e.g., a platform fee wallet) receives hundreds of concurrent transfers, each waits behind the previous one. This is a throughput bottleneck, not a correctness issue. Mitigated by the fact that most transfers involve distinct account pairs with no lock contention.
- **Lock wait escalation risk**: Default PostgreSQL lock wait is unlimited; a long-running transaction could block others. Mitigation: set `lock_timeout = 2s` at the session level in the JDBC connection (via `spring.datasource.hikari.connection-init-sql`); a lock timeout throws `LockAcquisitionException`, which is also caught by `@Retryable`.
- **Not compatible with read-replica routing**: Balance reads under `FOR UPDATE` must go to the primary replica. Read-replica offloading is only possible for non-locking queries (history, status lookups). This is acceptable given ASM-004.

### Neutral

- PostgreSQL's MVCC architecture means `SELECT FOR UPDATE` does not block plain `SELECT` queries on the same row; balance inquiry (`GET /balance`) is unaffected by ongoing transfers. This is a desirable property.
- Future migration to a `SKIP LOCKED` queue-based pattern (useful for fan-out to many wallets) would be additive and does not require removing the pessimistic lock; it would replace the direct `FOR UPDATE` with `FOR UPDATE SKIP LOCKED` in a separate processing-queue design.
