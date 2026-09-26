# ADR-003: Idempotency Key Table in PostgreSQL

**Date:** 2025-01-15  
**Status:** Accepted  
**SDD Reference:** SDD-20250115-001 §3, §8.1, §8.2.2, §10.2

---

## Context

The Payment Service must guarantee exactly-once execution of transfer requests (BO-002, REQ-F-007 through REQ-F-011). Clients experiencing network failures or timeouts must be able to retry a request with the same idempotency key without triggering a duplicate money movement. Three distinct scenarios must be handled:

1. **Completed transfer retried**: Return the cached response without executing a second transfer.
2. **In-progress transfer retried** (concurrent duplicate): Return HTTP 409 until the first request completes.
3. **Same key, different payload**: Return HTTP 422 — the key is bound to the original payload.

Two implementation options were evaluated:

### Option A — Redis Cache

Store idempotency key records in a Redis cache with a TTL. Use `SET key value NX PX ttl` (atomic set-if-not-exists) as the insertion gate.

**Problems:**
- **Operational complexity**: Redis requires its own high-availability deployment (Sentinel or Cluster), monitoring, and backup strategy. ASM-004 explicitly states that no external cache is required to meet latency SLAs at launch.
- **Durability gap**: Redis in-memory data can be lost on a crash if AOF/RDB persistence is not configured correctly. REQ-NF-015 requires idempotency key records to survive a service restart with zero loss.
- **Consistency boundary mismatch**: The idempotency key status transitions (PENDING → COMPLETED) must be coordinated with the PostgreSQL transaction commit. With Redis as the store, the payment DB transaction and the Redis write are in separate durability boundaries — a crash between commit and Redis update leaves the key in PENDING state permanently.
- **No foreign-key linkage**: The `transaction_id` stored in the idempotency record cannot be a DB-enforced FK to `transactions` if stored in Redis.

### Option B — PostgreSQL `idempotency_keys` Table (chosen)

Store idempotency key records in a PostgreSQL table with a `UNIQUE` constraint on the `key` column. Use a `try INSERT` pattern as the atomic insertion gate: the first concurrent request wins the unique constraint; all others detect the existing row.

**Key insight**: The same database transaction that writes the `transactions` and `ledger_entries` rows also writes the idempotency key record. A transaction rollback atomically undoes all three writes — the idempotency key status remains PENDING, allowing a retry.

---

## Decision

**We store idempotency key records in a `idempotency_keys` table in the same PostgreSQL database as the ledger.** The `UNIQUE` constraint on the `key` column is the atomic insertion gate; no Redis or other distributed cache is introduced.

Implementation specifics:

1. **Insertion gate** — `IdempotencyService.checkOrCreate()` attempts an `INSERT INTO idempotency_keys (key, payload_hash, status, created_at, expires_at) VALUES (...)`. If the `UNIQUE` constraint fires (`DataIntegrityViolationException`), the key already exists; the method reads the existing row and dispatches based on `status`.

2. **Payload binding** — the `payload_hash` column stores the SHA-256 hex digest of the canonical string `"{senderAccountId}|{receiverAccountId}|{amount.toPlainString()}"`. On every retry, the incoming request's hash is compared to the stored hash. A mismatch indicates key reuse with a different payload → `IDEMPOTENCY_KEY_CONFLICT` (REQ-F-010).

3. **Status transitions**:
   - On first insert: `status = PENDING`.
   - After transfer commits successfully: `status = COMPLETED`, `transaction_id = <txnId>`, `response_payload = <JSON>` set in a **separate** update (outside the transfer transaction, after commit). This is safe because a partial failure between transfer commit and idempotency update leaves the key in PENDING, which triggers a retry — the retry detects the committed transaction via the `transactions` table and promotes the key to COMPLETED.
   - After transfer fails (non-retryable): `status = FAILED`.

4. **In-progress detection** — if `status = PENDING` on read, `TransferInProgressException` is thrown → HTTP 409 (REQ-F-009). This handles the concurrent-duplicate scenario.

5. **Response caching** — `response_payload` is a `JSONB` column storing the serialised `TransferResponse` JSON. On a replay, the cached JSON is deserialised and returned without touching `TransferService` (REQ-F-008).

6. **TTL and cleanup** — `expires_at = created_at + ${app.idempotency.ttl-hours}` (default 24h, configurable without code change, REQ-F-011). A `@Scheduled` job runs daily and deletes rows where `expires_at < NOW()`. An index on `expires_at` ensures the deletion is a fast index range scan.

7. **Durability** — because the record is in PostgreSQL, it survives pod restarts, satisfying REQ-NF-015. No separate backup configuration is required beyond the existing DB backup.

---

## Consequences

### Positive

- **Zero additional infrastructure**: Idempotency is provided by the existing PostgreSQL instance; no Redis deployment, monitoring, or backup.
- **Durable by default**: All idempotency records participate in PostgreSQL's WAL-based durability (REQ-NF-015). No risk of cache eviction or OOM data loss.
- **Consistent with transfer transaction**: The key record and the ledger entries share the same DB and can be inspected together in a single SQL query — invaluable for debugging and audit.
- **Atomic in-progress detection**: The unique-constraint insert is the most atomic possible gate for concurrent duplicate detection — no race condition between "check if exists" and "insert".
- **FK-enforced integrity**: The `transaction_id` column in `idempotency_keys` is a FK to `transactions.id`. The data model is self-consistent and verifiable with standard DB tools.

### Negative

- **Additional write on every new transfer**: One extra DB INSERT per transfer (the idempotency key record). At 500 TPS this is 500 extra inserts/second; this is well within PostgreSQL's capacity and has negligible latency impact.
- **Table growth and TTL enforcement complexity**: The `idempotency_keys` table grows indefinitely without the cleanup job. If the cleanup job fails silently, the table becomes a data retention issue (OQ-002). Mitigation: alert on cleanup job failures; monitor table size.
- **TTL compliance risk**: The 24-hour default may not satisfy long-term audit retention for idempotency records (OQ-002). Mitigation: `app.idempotency.ttl-hours` is a configuration property; compliance team can set it to `8760` (1 year) or `61320` (7 years) without code change.
- **PENDING status leakage**: If the service crashes after inserting the idempotency key but before the transfer completes, the key is stuck in `PENDING`. On retry, the request receives HTTP 409. Mitigation: a background job promotes `PENDING` records older than `lock_timeout + 10s` to `FAILED` so retries can proceed. This is a Sprint 3 operational improvement.

### Neutral

- The `try INSERT / catch constraint violation` pattern is idiomatic in PostgreSQL and is preferred over `SELECT + INSERT` (which has a TOCTOU race condition). This pattern is safe and does not require application-level locking.
- SHA-256 is used for payload hashing rather than a full-payload equality check. This is a minor simplification; SHA-256 collision probability for the payload domain (UUIDs + decimal amounts) is negligible.
