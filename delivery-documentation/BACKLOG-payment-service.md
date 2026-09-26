# Product Backlog: Payment Service

**Version:** 1.0  **Status:** Draft  
**SRS Reference:** SRS-20250115-001  
**Team velocity:** 40 points/sprint  **Sprint duration:** 2 weeks  
**Team size:** 3 developers  
**Generated:** 2025-01-15

---

## Backlog Summary

| Epic | Stories | Total Points | Priority |
|---|---|---|---|
| EPIC-001: Transfer Execution & Ledger Correctness | US-008, US-001a, US-001b, US-009, US-010 | 18 | Must Have |
| EPIC-002: Idempotency & Exactly-Once Semantics | US-002 | 8 | Must Have |
| EPIC-003: Concurrency Safety & Balance Protection | US-007 | 5 | Must Have |
| EPIC-004: Balance Inquiry | US-003 | 5 | Must Have |
| EPIC-005: Transaction History | US-004 | 5 | Must Have / Should Have |
| EPIC-006: Transaction Status | US-005 | 3 | Must Have |
| EPIC-007: Transfer Reversal | US-006 | 8 | Must Have |
| **Total** | **10 stories** | **52 points** | |

> **Note — US-001 SPLIT:** The original US-001 (Initiate a Transfer) was estimated at 13 points and must be split per the 13+ rule. It was decomposed into US-001a (Transfer API Endpoint & Validation, 5 pts) and US-001b (Double-Entry Ledger Persistence, 5 pts).

---

## Epics

### EPIC-001: Transfer Execution & Ledger Correctness

**Goal:** Deliver a working transfer API backed by an immutable double-entry ledger so that money moves atomically and every mutation is permanently recorded.  
**REQ-IDs covered:** REQ-F-001, REQ-F-002, REQ-F-003, REQ-F-004, REQ-F-005, REQ-F-006, REQ-F-015, REQ-F-016, REQ-F-017  
**Business Objective:** BO-001, BO-004  
**Priority:** Must Have  
**Stories:** US-008, US-001a, US-001b, US-009, US-010

---

### EPIC-002: Idempotency & Exactly-Once Semantics

**Goal:** Guarantee that retried transfer requests never produce duplicate ledger entries or duplicate balance mutations, regardless of client retry behaviour.  
**REQ-IDs covered:** REQ-F-007, REQ-F-008, REQ-F-009, REQ-F-010, REQ-F-011  
**Business Objective:** BO-002  
**Priority:** Must Have  
**Stories:** US-002

---

### EPIC-003: Concurrency Safety & Balance Protection

**Goal:** Ensure that concurrent transfers involving the same account never produce negative balances, lost updates, or corrupted ledger state.  
**REQ-IDs covered:** REQ-F-012, REQ-F-013, REQ-F-014  
**Business Objective:** BO-003  
**Priority:** Must Have  
**Stories:** US-007

---

### EPIC-004: Balance Inquiry

**Goal:** Provide an accurate, low-latency read of an account's current (and historical point-in-time) balance so that users and callers always see authoritative fund availability.  
**REQ-IDs covered:** REQ-F-018, REQ-F-019  
**Business Objective:** BO-001, BO-005  
**Priority:** Must Have  
**Stories:** US-003

---

### EPIC-005: Transaction History

**Goal:** Allow users and compliance officers to retrieve a complete, paginated, filterable ledger view for any account so that payment disputes can be resolved and audits satisfied.  
**REQ-IDs covered:** REQ-F-020, REQ-F-021, REQ-F-022  
**Business Objective:** BO-004  
**Priority:** Must Have (REQ-F-020) / Should Have (REQ-F-021) / Could Have (REQ-F-022)  
**Stories:** US-004

---

### EPIC-006: Transaction Status

**Goal:** Enable any authorised caller to query the lifecycle state of a transfer by ID so that clients can recover from uncertainty about whether a transfer completed.  
**REQ-IDs covered:** REQ-F-023, REQ-F-024  
**Business Objective:** BO-001, BO-004  
**Priority:** Must Have  
**Stories:** US-005

---

### EPIC-007: Transfer Reversal

**Goal:** Allow finance team members to reverse a completed transfer by posting offsetting journal entries, correcting erroneous payments without mutating the original immutable ledger.  
**REQ-IDs covered:** REQ-F-025, REQ-F-026, REQ-F-027, REQ-F-015, REQ-F-016, REQ-F-017  
**Business Objective:** BO-004, BO-001  
**Priority:** Must Have  
**Stories:** US-006

---

## User Stories

> Stories are grouped by Epic and ordered by sprint assignment (Sprint 1 first, then Sprint 2).

---

### EPIC-001: Transfer Execution & Ledger Correctness

---

### US-008: Database Schema & Liquibase Foundation

**Epic:** EPIC-001  
**REQ-IDs:** REQ-F-001, REQ-F-005, REQ-F-015, CON-002, CON-003, CON-006  
**Story:** As a platform engineer, I want the service's PostgreSQL schema to be version-controlled in Liquibase so that all environments share an identical, reproducible data model from day one.

**INVEST validation:**
- Independent: Yes — no other story depends on being done first; this is the foundation.
- Negotiable: Yes — specific column names and index strategy are open to team review.
- Valuable: Yes — without a schema, no feature can be implemented or tested.
- Estimable: Yes — DDL work is well-understood; low uncertainty.
- Small: Yes — fits comfortably within one sprint.
- Testable: Yes — schema existence, constraints, and migrations can be verified via integration test.

**Vertical slice:** DB (schema) + Logic (Liquibase changeset execution at startup) + Test (migration smoke test)

**Acceptance Criteria:**
- Given a fresh PostgreSQL instance, when the application starts, then Liquibase applies all changesets without error and the `accounts`, `transactions`, `ledger_entries`, and `idempotency_keys` tables exist with the correct columns and constraints.
- Given the `accounts` table exists, when a balance update would make the balance column negative, then the database-level CHECK constraint rejects the UPDATE with a constraint violation.
- Given the `ledger_entries` table exists, when a row is inserted, then it accepts only `DEBIT` or `CREDIT` as `entry_type` values (enforced by CHECK or enum type).
- Given the `transactions` table, when a row is inserted, then `status` accepts only `PENDING`, `COMPLETED`, `FAILED`, `REVERSED`.

**Story Point Estimate:** 3 points  
**Estimation rationale:**
- Complexity drivers: Four interrelated tables with FK constraints, CHECK constraints, and index design.
- Risk/uncertainty: Low — DDL is deterministic; Liquibase changeset authoring is routine.
- Comparable to: A standard "create DB schema" task on a greenfield service.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-008-01 | Author Liquibase changelog master file and `accounts` table changeset (id, owner_user_id, balance BIGINT, currency, created_at, updated_at; CHECK balance >= 0) | DB | ~3h |
| ST-008-02 | Author `transactions` table changeset (id UUID PK, idempotency_key, sender_account_id FK, receiver_account_id FK, amount BIGINT, currency, status ENUM, created_at, updated_at) | DB | ~2h |
| ST-008-03 | Author `ledger_entries` table changeset (id UUID PK, transaction_id FK, account_id FK, entry_type ENUM DEBIT/CREDIT, amount BIGINT, currency, initiating_user_id, request_timestamp, reversal_of_entry_id nullable FK) | DB | ~3h |
| ST-008-04 | Author `idempotency_keys` table changeset (key UUID PK, request_hash, transaction_id FK nullable, status, created_at, expires_at) with index on `expires_at` for TTL cleanup | DB | ~2h |
| ST-008-05 | Write Spring Boot integration test asserting all four tables exist, CHECK constraints fire, and Liquibase reports zero pending changesets | Test | ~2h |

**Dependencies:** None  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

<!-- SPLIT: US-001 original estimate was 13 points — split into US-001a (Transfer API Endpoint & Validation, 5 pts) and US-001b (Double-Entry Ledger Persistence, 5 pts) because the story spanned REST controller design, input validation, business-rule rejection logic, ledger entry creation, and net-zero enforcement — five distinct vertical concerns each large enough to warrant independent stories. -->

### US-001a: Transfer API Endpoint & Input Validation

**Epic:** EPIC-001  
**REQ-IDs:** REQ-F-001, REQ-F-002, REQ-F-003, REQ-F-004, REQ-F-005, REQ-F-006  
**Story:** As a sender, I want to submit a transfer request via a REST endpoint so that the API validates my request, rejects invalid inputs immediately, and returns a transaction ID on success.

**INVEST validation:**
- Independent: Yes — depends only on US-008 (schema), not on US-001b; the controller and validation layer can be built and tested with a stub ledger.
- Negotiable: Yes — request/response envelope format is open per CON-005.
- Valuable: Yes — provides the caller-facing API contract; enables end-to-end testing from this story forward.
- Estimable: Yes — Spring MVC controller + Bean Validation is well-understood; moderate complexity from four distinct rejection paths.
- Small: Yes — fits in one sprint at 5 points.
- Testable: Yes — all four rejection scenarios (insufficient funds, zero/negative amount, self-transfer, missing idempotency key) are precisely specified.

**Vertical slice:** API (REST controller, request/response DTOs) + Logic (input validation, balance pre-check, service method) + DB (account balance read) + Test (unit + integration)

**Acceptance Criteria:**
- Given a sender with balance 10000 cents and a valid receiver, when POST /v1/transfers is called with amount 2500 cents and a unique idempotency key, then the API returns HTTP 201 Created with a non-null `transactionId` and `status: COMPLETED`.
- Given a sender with balance 1000 cents, when POST /v1/transfers is called with amount 5000 cents, then the API returns HTTP 422 with error code `INSUFFICIENT_FUNDS` and no transaction is persisted.
- Given a valid sender, when POST /v1/transfers is called with amount 0 cents, then the API returns HTTP 400 with error code `INVALID_AMOUNT`.
- Given a valid sender, when POST /v1/transfers is called with `receiverAccountId` equal to `senderAccountId`, then the API returns HTTP 422 with error code `SELF_TRANSFER_NOT_ALLOWED`.
- Given a transfer request, when the `Idempotency-Key` header is absent, then the API returns HTTP 400 with error code `MISSING_IDEMPOTENCY_KEY`.

**Story Point Estimate:** 5 points  
**Estimation rationale:**
- Complexity drivers: Four validation paths with distinct HTTP status codes, DTO design, Spring controller, service interface definition.
- Risk/uncertainty: Low — patterns are well-established in Spring Boot; no external integrations.
- Comparable to: US-005 (Transfer Status) but with significantly more validation paths.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-001a-01 | Define `TransferRequest` and `TransferResponse` DTOs with Bean Validation annotations (`@NotNull`, `@Positive`, `@NotBlank`) | API | ~2h |
| ST-001a-02 | Implement `TransferController` (`@RestController`, `@PostMapping("/v1/transfers")`) with `@RequestHeader("Idempotency-Key")` binding and `@Valid` body | API | ~3h |
| ST-001a-03 | Implement `TransferService.initiateTransfer()` — load sender/receiver accounts, apply pre-checks (amount > 0, sender ≠ receiver, sufficient balance), return early with domain exception on failure | Logic | ~4h |
| ST-001a-04 | Implement `GlobalExceptionHandler` mapping domain exceptions (`InsufficientFundsException`, `SelfTransferException`, `InvalidAmountException`, `MissingIdempotencyKeyException`) to correct HTTP status codes and error envelopes | API | ~3h |
| ST-001a-05 | Write unit tests for `TransferService` covering all four rejection paths (Mockito, no DB) | Test | ~3h |
| ST-001a-06 | Write Spring Boot integration tests (`@SpringBootTest`) for POST /v1/transfers covering success + all four rejection scenarios using test PostgreSQL (Testcontainers) | Test | ~3h |

**Dependencies:** US-008 (schema must exist)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### US-001b: Double-Entry Ledger Persistence

**Epic:** EPIC-001  
**REQ-IDs:** REQ-F-001, REQ-F-005, REQ-F-015, REQ-F-016, REQ-F-017  
**Story:** As a finance auditor, I want every successful transfer to produce exactly two immutable ledger entries (one DEBIT, one CREDIT) whose amounts sum to net zero, so that the books always balance.

**INVEST validation:**
- Independent: Yes — ledger persistence logic is encapsulated in `LedgerService`; US-001a calls it via interface, allowing parallel development once US-008 is done.
- Negotiable: Yes — internal ledger entry structure is open to review; the net-zero invariant is non-negotiable.
- Valuable: Yes — without this story, the transfer API produces no audit trail; compliance gates cannot be met.
- Estimable: Yes — JPA entity + transactional service + net-zero assertion is well-scoped.
- Small: Yes — 5 points fits in one sprint.
- Testable: Yes — net-zero can be asserted in a DB-level query; immutability via absence of UPDATE/DELETE on ledger table.

**Vertical slice:** DB (ledger_entries and transactions writes) + Logic (`@Transactional` ledger service, net-zero assertion) + Test (integration, DB query verification)

**Acceptance Criteria:**
- Given a successful transfer of 2500 cents from Account A to Account B, when the transaction is committed, then exactly two `ledger_entries` rows exist for that `transaction_id` — one DEBIT for Account A and one CREDIT for Account B, both for 2500 cents.
- Given those two ledger entries, when their amounts are summed with sign (DEBIT negative, CREDIT positive), then the result is exactly zero.
- Given a committed ledger entry, when an UPDATE or DELETE is attempted on that row (e.g., via a direct JDBC call in a test), then the operation is rejected (enforced by application-layer guard or DB trigger).
- Given a transfer that fails (e.g., insufficient funds), when the transaction rolls back, then zero ledger entries exist for that attempted transfer.
- Given a reversal scenario (future), when a new reversal transaction is created, then its ledger entries reference the original `transaction_id` via `reversal_of_entry_id`.

**Story Point Estimate:** 5 points  
**Estimation rationale:**
- Complexity drivers: Atomic multi-row insert within a single `@Transactional` boundary, net-zero invariant enforcement, immutability guard.
- Risk/uncertainty: Low-medium — Spring `@Transactional` is well understood; the immutability enforcement approach (application vs. DB trigger) needs a team decision.
- Comparable to: US-001a in size; slightly deeper DB concern.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-001b-01 | Implement `LedgerEntry` JPA entity mapping `ledger_entries` table (all columns from ST-008-03); mark entity class `@Immutable` and override `merge()` to throw | DB | ~3h |
| ST-001b-02 | Implement `Transaction` JPA entity mapping `transactions` table; define `TransactionStatus` enum (`PENDING`, `COMPLETED`, `FAILED`, `REVERSED`) | DB | ~2h |
| ST-001b-03 | Implement `LedgerService.recordTransfer()` — inside a `@Transactional` method, persist one DEBIT entry and one CREDIT entry, then assert `sum(DEBIT) == sum(CREDIT)` for the transaction; throw `LedgerIntegrityException` if assertion fails | Logic | ~4h |
| ST-001b-04 | Wire `TransferService` (from US-001a) to call `LedgerService.recordTransfer()` after balance updates; ensure both balance mutation and ledger writes share the same transaction boundary | Logic | ~3h |
| ST-001b-05 | Write unit tests for `LedgerService` (mock repository): assert two entries created, net-zero assertion fires on corrupt input | Test | ~3h |
| ST-001b-06 | Write integration test querying `ledger_entries` directly after a transfer: verify row count = 2, DEBIT + CREDIT = 0 net, and that a direct update attempt throws | Test | ~3h |

**Dependencies:** US-008 (schema), US-001a (service interface defined)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-003: Concurrency Safety & Balance Protection

---

### US-007: Concurrency-Safe Balance Deduction

**Epic:** EPIC-003  
**REQ-IDs:** REQ-F-012, REQ-F-013, REQ-F-014  
**Story:** As a platform engineer, I want all concurrent transfers involving the same account to be serialised with pessimistic row locks so that no account ever goes negative and no balance update is silently lost.

**INVEST validation:**
- Independent: Yes — locking strategy is a concern within `TransferService`; it does not depend on idempotency (US-002) or reversal (US-006).
- Negotiable: Yes — lock acquisition order (by account ID sort), retry count, and backoff parameters are open for team tuning.
- Valuable: Yes — without this story, BO-003 is unmet; a race condition destroying a balance would be a critical production incident.
- Estimable: Yes — `SELECT FOR UPDATE` with ordered locking is a known pattern; deadlock retry is a finite implementation.
- Small: Yes — 5 points; focused on one concern within the service layer.
- Testable: Yes — concurrent test (two threads, same account, only one can succeed) is a deterministic integration test.

**Vertical slice:** DB (`SELECT FOR UPDATE` query) + Logic (lock ordering, retry with backoff) + Test (concurrent integration test)

**Acceptance Criteria:**
- Given account A has a balance of 1000 cents and two concurrent requests each attempt to debit 800 cents, when both execute simultaneously, then exactly one succeeds (balance → 200 cents) and the other returns HTTP 422 `INSUFFICIENT_FUNDS`; the final balance is exactly 200 cents.
- Given a deadlock occurs on the first attempt, when the service retries with exponential backoff, then the transfer succeeds within 3 retry attempts without creating duplicate ledger entries.
- Given the retry budget is exhausted (3 retries all deadlock), when the service gives up, then HTTP 503 `TRANSFER_DEADLOCK_EXHAUSTED` is returned and zero ledger entries are created.
- Given two accounts A (id=1) and B (id=2) are locked in a transfer, when the service acquires locks, then it always acquires the lock on the lower account ID first (deadlock prevention by ordering).

**Story Point Estimate:** 5 points  
**Estimation rationale:**
- Complexity drivers: `SELECT FOR UPDATE NOWAIT` or `FOR UPDATE` in JPQL/native query, consistent lock ordering, `@Retryable` configuration with exponential backoff, concurrent integration test setup.
- Risk/uncertainty: Medium — deadlock retry and Testcontainers-based concurrent test have some setup complexity.
- Comparable to: US-001b in implementation depth.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-007-01 | Add `AccountRepository.findByIdForUpdate(List<UUID> ids)` using `@Lock(LockModeType.PESSIMISTIC_WRITE)` native query with `ORDER BY id` to enforce consistent lock acquisition order | DB | ~3h |
| ST-007-02 | Refactor `TransferService.initiateTransfer()` to call `findByIdForUpdate` with sorted ID list before reading balances; remove any prior optimistic-lock path | Logic | ~2h |
| ST-007-03 | Implement deadlock retry using Spring Retry `@Retryable(value = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))` on `initiateTransfer()` | Logic | ~3h |
| ST-007-04 | Add `@Recover` method on retry exhaustion to throw `DeadlockExhaustedException`; wire to `GlobalExceptionHandler` → HTTP 503 | Logic | ~2h |
| ST-007-05 | Write Testcontainers-based concurrent integration test: 2 threads × 800-cent debit on 1000-cent account; assert exactly one success, final balance = 200, zero extra ledger entries | Test | ~4h |

**Dependencies:** US-008 (schema), US-001b (transfer service exists)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-002: Idempotency & Exactly-Once Semantics

---

### US-002: Idempotent Transfer Execution

**Epic:** EPIC-002  
**REQ-IDs:** REQ-F-007, REQ-F-008, REQ-F-009, REQ-F-010, REQ-F-011  
**Story:** As a sender whose network connection dropped, I want to retry my transfer request with the same idempotency key so that my money is moved exactly once regardless of how many times the request is sent.

**INVEST validation:**
- Independent: Yes — idempotency layer wraps the transfer execution; depends on US-001a + US-001b being complete (service exists), but is a self-contained concern.
- Negotiable: Yes — storage approach (DB table vs. Redis) is open; DB approach aligned with ASM-004.
- Valuable: Yes — without idempotency, every client retry risks a double charge; this is a core trust feature.
- Estimable: Yes — idempotency key lookup + hash comparison is a well-understood pattern; HTTP 409 for in-progress is the main complexity.
- Small: Yes — 8 points; large but fits in one sprint at team velocity.
- Testable: Yes — three distinct retry scenarios with precise expected HTTP responses.

**Vertical slice:** API (idempotency key header extraction) + Logic (key lookup, hash comparison, in-progress guard) + DB (idempotency_keys table read/write) + Test

**Acceptance Criteria:**
- Given transfer "idk-abc-123" completed successfully, when the same request (same key, same payload) is retried, then HTTP 200 is returned with the original `transactionId` and `status: COMPLETED` and no new ledger entries are created.
- Given transfer "idk-xyz-456" is `PENDING` (in-progress), when a second request arrives with the same key, then HTTP 409 is returned with error code `TRANSFER_IN_PROGRESS`.
- Given transfer "idk-def-789" completed for 100 cents, when a new request uses the same key but for 200 cents, then HTTP 422 is returned with error code `IDEMPOTENCY_KEY_CONFLICT` and no new transfer is executed.
- Given an idempotency key record was created more than 24 hours ago, when a scheduled cleanup runs, then the record is eligible for deletion (retention ≥ 24 hours, satisfying REQ-F-011).
- Given a successful transfer, when the service restarts (pod restart), then the idempotency key record survives in PostgreSQL and a retry after restart still returns the original response (REQ-NF-015).

**Story Point Estimate:** 8 points  
**Estimation rationale:**
- Complexity drivers: In-progress detection race condition (two concurrent retries of the same key), payload hash comparison, TTL management, recovery after pod restart.
- Risk/uncertainty: Medium — the "in-progress" 409 path requires careful concurrency handling (DB unique constraint on key as the atomic gate).
- Comparable to: US-006 (Transfer Reversal) in overall complexity.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-002-01 | Implement `IdempotencyKeyRepository` (JPA): `findByKey()`, `insertKey()` using DB unique constraint on `key` column as the atomicity gate for concurrent inserts | DB | ~3h |
| ST-002-02 | Implement `IdempotencyService.checkOrCreate()`: (1) attempt INSERT with status `PENDING`; if unique constraint fires → key exists; (2) if key exists + status `COMPLETED` → return cached response; (3) if `PENDING` → throw `TransferInProgressException`; (4) if payload hash mismatch → throw `IdempotencyKeyConflictException` | Logic | ~5h |
| ST-002-03 | Implement SHA-256 payload hash of `{senderAccountId, receiverAccountId, amount}` stored on first request; compare on retry | Logic | ~2h |
| ST-002-04 | Wire `IdempotencyService.checkOrCreate()` as a pre-check in `TransferController` before delegating to `TransferService`; update key status to `COMPLETED` with transaction ID after success | Logic | ~3h |
| ST-002-05 | Implement `@Scheduled` cleanup task deleting idempotency_keys where `expires_at < NOW()` (expires_at = created_at + 24h minimum; configurable via `app.idempotency.ttl-hours`) | Logic | ~2h |
| ST-002-06 | Write unit tests for `IdempotencyService`: all three key-exists paths (cached, in-progress, conflict), hash mismatch detection | Test | ~3h |
| ST-002-07 | Write integration tests: retry-after-success, retry-while-in-progress, key-with-different-payload; verify ledger_entries count stays at 2 on all retry paths | Test | ~4h |

**Dependencies:** US-008 (schema), US-001a (controller exists), US-001b (service exists)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-004: Balance Inquiry

---

### US-003: Balance Inquiry Endpoint

**Epic:** EPIC-004  
**REQ-IDs:** REQ-F-018, REQ-F-019  
**Story:** As a user, I want to query my account balance (current or as-of a past timestamp) so that I know exactly how much money is available before making a transfer.

**INVEST validation:**
- Independent: Yes — read-only query; depends only on US-008 (schema exists with populated accounts).
- Negotiable: Yes — point-in-time balance calculation approach (sum ledger entries vs. snapshot) is open.
- Valuable: Yes — foundational UX feature; users cannot make informed transfers without knowing their balance.
- Estimable: Yes — simple GET endpoint + one DB query; point-in-time adds a parameterised query.
- Small: Yes — 5 points; two acceptance scenarios, well-scoped.
- Testable: Yes — exact balance values can be asserted; 404 for missing account is deterministic.

**Vertical slice:** API (GET endpoint) + Logic (balance query, point-in-time calculation) + DB (account read + ledger sum query) + Test

**Acceptance Criteria:**
- Given account "acc-001" has a balance of 7500 cents, when GET /v1/accounts/acc-001/balance is called, then HTTP 200 is returned with `{ "balance": 7500, "currency": "USD", "accountId": "acc-001" }`.
- Given account "acc-999" does not exist, when GET /v1/accounts/acc-999/balance is called, then HTTP 404 is returned with error code `ACCOUNT_NOT_FOUND`.
- Given account "acc-001" had a balance of 5000 cents at 2024-01-01T00:00:00Z and has since changed, when GET /v1/accounts/acc-001/balance?asOf=2024-01-01T00:00:00Z is called, then HTTP 200 is returned with `{ "balance": 5000 }`.
- Given a balance inquiry, when the response arrives, then p95 latency is ≤ 100 ms under normal load (REQ-NF-002).

**Story Point Estimate:** 5 points  
**Estimation rationale:**
- Complexity drivers: Point-in-time balance requires a SUM query over `ledger_entries` filtered by timestamp — more expensive than a simple account row read.
- Risk/uncertainty: Low-medium — point-in-time query performance depends on index design (covered by US-008 index on `request_timestamp`).
- Comparable to: US-005 (Transfer Status), slightly larger due to point-in-time query.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-003-01 | Implement `BalanceController` (`@GetMapping("/v1/accounts/{accountId}/balance")`) with optional `?asOf=` query param; return `BalanceResponse` DTO | API | ~2h |
| ST-003-02 | Implement `AccountService.getCurrentBalance(UUID accountId)` — fetch account row; throw `AccountNotFoundException` if missing; return `balance` field | Logic | ~2h |
| ST-003-03 | Implement `AccountService.getBalanceAsOf(UUID accountId, Instant asOf)` — SUM CREDIT entries minus SUM DEBIT entries from `ledger_entries` where `account_id = ? AND request_timestamp <= ?` | Logic + DB | ~4h |
| ST-003-04 | Add DB index on `ledger_entries(account_id, request_timestamp)` via Liquibase changeset (addendum to US-008 changelog) | DB | ~1h |
| ST-003-05 | Write unit tests for `AccountService`: current balance path, point-in-time path, account not found | Test | ~2h |
| ST-003-06 | Write integration tests for GET /v1/accounts/{id}/balance: current balance, 404, and point-in-time scenario with pre-seeded ledger entries | Test | ~3h |

**Dependencies:** US-008 (schema)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-006: Transaction Status

---

### US-005: Transfer Status Endpoint

**Epic:** EPIC-006  
**REQ-IDs:** REQ-F-023, REQ-F-024  
**Story:** As a sender, I want to query the status and details of a previously submitted transfer by its transaction ID so that I can resolve uncertainty about whether the transfer completed.

**INVEST validation:**
- Independent: Yes — read-only lookup against `transactions` table; depends only on US-008 and US-001b (which persists transaction rows).
- Negotiable: Yes — response payload fields are open to review within the JSON:API envelope.
- Valuable: Yes — clients need this to distinguish between "still pending" and "definitely failed" without resubmitting.
- Estimable: Yes — simple GET + JPA findById; very low complexity.
- Small: Yes — 3 points; one happy path, one 404 path.
- Testable: Yes — status values are a closed enum; 404 is deterministic.

**Vertical slice:** API (GET endpoint) + Logic (transaction lookup, status mapping) + DB (transactions table read) + Test

**Acceptance Criteria:**
- Given transaction "txn-001" is in COMPLETED status, when GET /v1/transfers/txn-001 is called, then HTTP 200 is returned with `status: COMPLETED`, `amount`, `senderAccountId`, `receiverAccountId`, and `createdAt`.
- Given "txn-999" does not exist, when GET /v1/transfers/txn-999 is called, then HTTP 404 is returned with error code `TRANSACTION_NOT_FOUND`.
- Given a transfer in PENDING status, when GET /v1/transfers/{id} is called, then HTTP 200 is returned with `status: PENDING`.

**Story Point Estimate:** 3 points  
**Estimation rationale:**
- Complexity drivers: Minimal — single-table read, closed-enum status, standard DTO mapping.
- Risk/uncertainty: Very low — no writes, no concurrency concern.
- Comparable to: Simpler than US-003 (no optional params or computed fields).

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-005-01 | Implement `TransactionController` (`@GetMapping("/v1/transfers/{transactionId}")`) returning `TransactionStatusResponse` DTO | API | ~2h |
| ST-005-02 | Implement `TransactionService.getTransaction(UUID id)` — `JpaRepository.findById()`; throw `TransactionNotFoundException` if absent | Logic | ~1h |
| ST-005-03 | Write unit + integration tests: COMPLETED response, PENDING response, 404 for unknown ID | Test | ~3h |

**Dependencies:** US-008 (schema), US-001b (transaction rows written by transfer)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-001 (continued): Observability & API Scaffolding

---

### US-009: Service Observability & Health Endpoints

**Epic:** EPIC-001  
**REQ-IDs:** REQ-NF-012, CON-007, CON-008  
**Story:** As a platform engineer, I want the service to emit structured JSON logs, distributed trace spans, and Kubernetes health probes so that it can be safely operated, monitored, and deployed in production.

**INVEST validation:**
- Independent: Yes — cross-cutting infrastructure concern; can be implemented in parallel with business logic stories.
- Negotiable: Yes — trace export target and log format fields are configurable.
- Valuable: Yes — without this, the service cannot be safely deployed to production or included in the observability platform.
- Estimable: Yes — Spring Boot Actuator + Logback JSON encoder + Micrometer OpenTelemetry is a well-known configuration set.
- Small: Yes — 3 points; mostly configuration, no complex business logic.
- Testable: Yes — health endpoints return 200; log output is structured JSON; trace spans verifiable in integration test.

**Vertical slice:** API (/health/liveness, /health/readiness endpoints) + Logic (OpenTelemetry span instrumentation on service methods) + Test (actuator integration test)

**Acceptance Criteria:**
- Given a running service, when GET /health/liveness is called, then HTTP 200 is returned with `{ "status": "UP" }`.
- Given a running service, when GET /health/readiness is called, then HTTP 200 is returned; when the database is unreachable, then HTTP 503 is returned.
- Given any inbound request to the service, when the request completes, then exactly one OpenTelemetry trace span is emitted containing `http.method`, `http.url`, and `db.statement` attributes (REQ-NF-012).
- Given the service writes to stdout, when a log line is examined, then it is valid JSON containing `timestamp`, `level`, `logger`, `message`, and `traceId` fields (CON-007).

**Story Point Estimate:** 3 points  
**Estimation rationale:**
- Complexity drivers: Logback JSON encoder config, Micrometer OTLP export, Actuator endpoint customisation.
- Risk/uncertainty: Low — these are standard Spring Boot ecosystem integrations.
- Comparable to: US-008 (infrastructure/config in nature).

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-009-01 | Configure `logback-spring.xml` with `logstash-logback-encoder` for structured JSON stdout output; include `traceId` MDC field | Logic | ~2h |
| ST-009-02 | Add `spring-boot-starter-actuator` + configure `/health/liveness` and `/health/readiness` Kubernetes probes with DB datasource health indicator | API | ~2h |
| ST-009-03 | Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` dependencies; configure `OTLP_ENDPOINT` via environment variable; add `@WithSpan` on `TransferService.initiateTransfer()` | Logic | ~3h |
| ST-009-04 | Write integration test asserting `/health/liveness` → 200, `/health/readiness` → 200 with live DB; verify log output contains `traceId` key | Test | ~2h |

**Dependencies:** US-008 (Spring Boot app must be runnable)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### US-010: OpenAPI Spec & API Versioning

**Epic:** EPIC-001  
**REQ-IDs:** REQ-NF-014, REQ-NF-018, CON-005  
**Story:** As a platform engineer, I want the service to publish a machine-readable OpenAPI 3.x spec at `/v1/openapi.json` and version all endpoints under `/v1/` so that API consumers can integrate reliably and non-breaking evolutions are possible.

**INVEST validation:**
- Independent: Yes — can be applied as a configuration layer on top of existing controllers.
- Negotiable: Yes — specific OpenAPI annotations and doc structure are team-determined.
- Valuable: Yes — API contract documentation is required for interoperability and future versioning (REQ-NF-014, REQ-NF-018).
- Estimable: Yes — SpringDoc OpenAPI auto-generation is well-understood; 2 points.
- Small: Yes — 2 points; primarily configuration.
- Testable: Yes — `/v1/openapi.json` must return a valid OpenAPI 3.x document.

**Vertical slice:** API (URI versioning `/v1/`, OpenAPI spec endpoint) + Test (spec validation)

**Acceptance Criteria:**
- Given a running service, when GET /v1/openapi.json is called, then a valid OpenAPI 3.x document is returned with zero validation errors against the OpenAPI 3.x schema.
- Given all REST endpoints in the service, when each path is examined, then every path starts with `/v1/`.
- Given the OpenAPI spec, when it is parsed, then every endpoint has at least one documented response code and a non-empty `description`.

**Story Point Estimate:** 2 points  
**Estimation rationale:**
- Complexity drivers: Minimal — SpringDoc generates the spec from annotations; URI prefix is a global Spring MVC config.
- Risk/uncertainty: Very low.
- Comparable to: Configuration task similar in scope to ST-009-02.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-010-01 | Add `springdoc-openapi-starter-webmvc-ui` dependency; configure `springdoc.api-docs.path=/v1/openapi.json`; add `@OpenAPIDefinition` with title/version | API | ~2h |
| ST-010-02 | Add `@Operation` and `@ApiResponse` annotations to all controllers (TransferController, BalanceController, TransactionController) | API | ~3h |
| ST-010-03 | Write integration test calling GET /v1/openapi.json, asserting HTTP 200 and `openapi: "3.x.x"` field present | Test | ~1h |

**Dependencies:** US-001a, US-003, US-005 (controllers must exist to annotate)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-007: Transfer Reversal

---

### US-006: Reverse a Completed Transfer

**Epic:** EPIC-007  
**REQ-IDs:** REQ-F-025, REQ-F-026, REQ-F-027, REQ-F-015, REQ-F-016, REQ-F-017  
**Story:** As a finance team member, I want to reverse a completed transfer by creating offsetting journal entries so that erroneous payments are corrected without altering the original immutable ledger.

**INVEST validation:**
- Independent: Yes — reversal is a new POST endpoint and new service method; depends on US-001b (ledger exists) and US-007 (locking, since reversal also modifies balances).
- Negotiable: Yes — authorisation check mechanism (role header vs. future RBAC) is open per OQ-003.
- Valuable: Yes — finance/compliance cannot meet their audit obligations without the ability to correct erroneous transfers.
- Estimable: Yes — the logic mirrors the forward transfer but with swapped DEBIT/CREDIT roles; same locking and ledger patterns apply.
- Small: Yes — 8 points; largest story in Sprint 2 but fits within sprint capacity.
- Testable: Yes — three SRS-specified scenarios with precise HTTP responses and ledger entry counts.

**Vertical slice:** API (POST /v1/transfers/{id}/reverse) + Logic (reversal service, state-machine check) + DB (offsetting ledger entry writes, status update) + Test

**Acceptance Criteria:**
- Given transfer "txn-100" is COMPLETED and the original receiver has sufficient balance, when POST /v1/transfers/txn-100/reverse is called by an authorised caller, then HTTP 201 is returned with a new `reversalTransactionId`, the original sender's balance is incremented by the original amount, the receiver's balance is decremented, two new offsetting ledger entries are created referencing `txn-100`, and `txn-100` status is updated to REVERSED.
- Given transfer "txn-200" is in REVERSED status, when POST /v1/transfers/txn-200/reverse is called, then HTTP 422 is returned with error code `TRANSFER_ALREADY_REVERSED` and no ledger entries are created.
- Given transfer "txn-300" is COMPLETED but the original receiver has insufficient balance, when POST /v1/transfers/txn-300/reverse is called, then HTTP 422 is returned with error code `INSUFFICIENT_FUNDS_FOR_REVERSAL` and no balances are changed.
- Given transfer "txn-400" is in FAILED status, when a reversal is attempted, then HTTP 422 is returned with error code `TRANSFER_NOT_REVERSIBLE`.
- Given a reversal creates two new ledger entries, when their amounts are summed (signed), then the net is exactly zero.

**Story Point Estimate:** 8 points  
**Estimation rationale:**
- Complexity drivers: State-machine guard (only COMPLETED is reversible), locked balance mutations (same pessimistic lock as forward transfer), link from reversal entries to original transaction ID, two new test scenarios beyond forward-transfer tests.
- Risk/uncertainty: Medium — OQ-003 (who can call reversal) is unresolved; assumption: any authenticated caller in this sprint; access control hardening deferred.
- Comparable to: US-002 (Idempotency) in overall complexity.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-006-01 | Implement `ReversalController` (`@PostMapping("/v1/transfers/{transactionId}/reverse")`); return `ReversalResponse` DTO with `reversalTransactionId` | API | ~2h |
| ST-006-02 | Implement `ReversalService.reverse(UUID transactionId)`: load original transaction; assert status == COMPLETED (throw `TransferNotReversibleException` otherwise); check receiver has sufficient balance (throw `InsufficientFundsForReversalException` if not) | Logic | ~4h |
| ST-006-03 | Within `ReversalService.reverse()`, inside a `@Transactional` method: (1) acquire pessimistic locks on both accounts in sorted-ID order (reuse lock logic from US-007); (2) update balances (increment sender, decrement receiver); (3) call `LedgerService` to write two offsetting entries with `reversal_of_entry_id` set | Logic + DB | ~4h |
| ST-006-04 | Update original transaction status to REVERSED within the same transaction; create a new `transactions` row for the reversal event linked to the original | DB | ~2h |
| ST-006-05 | Wire `GlobalExceptionHandler` for `TransferNotReversibleException` and `InsufficientFundsForReversalException` → HTTP 422 with correct error codes | API | ~1h |
| ST-006-06 | Write unit tests for `ReversalService`: already-reversed guard, FAILED guard, insufficient-balance guard, successful reversal (mocked repositories) | Test | ~3h |
| ST-006-07 | Write integration tests: successful reversal (verify ledger rows, balance changes, status = REVERSED), reversal-of-reversed (422), reversal with zero receiver balance (422) | Test | ~4h |

**Dependencies:** US-008 (schema), US-001b (ledger service), US-007 (locking — reused)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

### EPIC-005: Transaction History

---

### US-004: Paginated Transaction History

**Epic:** EPIC-005  
**REQ-IDs:** REQ-F-020, REQ-F-021, REQ-F-022  
**Story:** As a user, I want to view my paginated transaction history with optional date-range and type filters so that I can review all payments and resolve disputes.

**INVEST validation:**
- Independent: Yes — read-only query; depends on US-008 (schema) and the existence of ledger entries (US-001b).
- Negotiable: Yes — pagination strategy (offset vs. cursor) is open per OQ-005; offset is implemented here with a note to migrate to cursor-based if needed.
- Valuable: Yes — users cannot track their payment activity without this feature; compliance officers need it for audits.
- Estimable: Yes — Spring Data JPA `Pageable` with a dynamic specification query is well-understood.
- Small: Yes — 5 points; three acceptance scenarios.
- Testable: Yes — page count, sort order, date-range exclusion, and empty-list responses are all deterministic.

**Vertical slice:** API (GET endpoint with query params) + Logic (dynamic filter specification, pagination) + DB (indexed query on ledger_entries) + Test

**Acceptance Criteria:**
- Given account "acc-001" has 50 ledger entries, when GET /v1/accounts/acc-001/transactions?page=1&pageSize=20 is called, then HTTP 200 is returned with 20 entries sorted by `requestTimestamp` descending and pagination metadata `{ totalCount: 50, page: 1, pageSize: 20, totalPages: 3 }`.
- Given account "acc-001" has transactions spanning multiple months, when GET /v1/accounts/acc-001/transactions?from=2024-01-01&to=2024-01-31 is called, then only entries with timestamps in January 2024 are returned.
- Given account "acc-001" exists but has no transactions, when GET /v1/accounts/acc-001/transactions is called, then HTTP 200 is returned with `{ entries: [], totalCount: 0 }`.
- Given a type filter is applied, when GET /v1/accounts/acc-001/transactions?type=DEBIT is called, then only DEBIT entries are returned (REQ-F-022).

**Story Point Estimate:** 5 points  
**Estimation rationale:**
- Complexity drivers: Dynamic JPA Specification for optional date-range and type filters, pagination metadata in response envelope, performance requirement (p95 ≤ 200 ms for first page per REQ-NF-016).
- Risk/uncertainty: Low-medium — OQ-005 (cursor pagination) is unresolved; offset pagination is a known technical debt item to flag.
- Comparable to: US-003 (Balance Inquiry) but with more query complexity.

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-004-01 | Implement `TransactionHistoryController` (`@GetMapping("/v1/accounts/{accountId}/transactions")`) with `@RequestParam` for `page`, `pageSize`, `from`, `to`, `type`; return `PagedTransactionResponse` DTO with entries + pagination metadata | API | ~3h |
| ST-004-02 | Implement `LedgerEntrySpecification` (Spring Data JPA `Specification<LedgerEntry>`): `accountId` equals, optional `requestTimestamp` between, optional `entryType` equals | Logic | ~3h |
| ST-004-03 | Implement `TransactionHistoryService.getHistory(UUID accountId, HistoryFilter filter, Pageable pageable)` using `LedgerEntryRepository.findAll(spec, pageable)` | Logic | ~2h |
| ST-004-04 | Add composite DB index on `ledger_entries(account_id, request_timestamp DESC)` via Liquibase changeset addendum | DB | ~1h |
| ST-004-05 | Write unit tests for `LedgerEntrySpecification`: no-filter, date-range-only, type-only, combined filter (verify SQL predicates via Criteria metamodel) | Test | ~2h |
| ST-004-06 | Write integration tests: 50-entry pagination, date-range filter, type filter, empty result | Test | ~3h |

**Dependencies:** US-008 (schema), US-001b (ledger entries populated)  
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass

---

## Assumptions

| ID | Assumption | Impact if Wrong |
|---|---|---|
| ASM-001 | JWT validation is handled by the API Gateway; the service trusts `X-User-Id` header for `initiatingUserId` on ledger entries. | Service would need its own JWT validation; increases scope significantly. |
| ASM-002 | All monetary amounts are USD (single currency). | Currency column in ledger + FX logic would be needed. |
| ASM-003 | Account rows are seeded by an external Account Service and exist in the payment DB as FK references only. | Account creation flows enter scope. |
| ASM-004 | PostgreSQL `SELECT FOR UPDATE` with consistent lock ordering is sufficient concurrency control at ≥ 500 TPS without a separate Redis lock. | Redis distributed lock or SKIP LOCKED pattern would be needed. |
| ASM-005 | Per-transaction transfer limit is confirmed by Product before Sprint 1; implementation assumes no overflow risk for now. | Overflow safety check must be added to transfer validation. |
| ASM-006 | Reversal is callable by any authenticated caller for Sprint 2; role-based restriction is a Sprint 3+ concern (pending OQ-003). | RBAC implementation would add scope to US-006. |
| ASM-007 | Offset-based pagination is acceptable for Sprint 2; cursor-based migration is deferred pending OQ-005. | Pagination migration adds a Sprint 3 story. |

---

## Open Questions

| ID | Question | Owner | Status |
|---|---|---|---|
| OQ-001 | What is the maximum single-transaction transfer amount? Affects overflow safety check in transfer validation. | Product Team | Open — must resolve before Sprint 1 kickoff |
| OQ-002 | Required idempotency key retention beyond 24 hours? Finance/compliance may require 90-day or 7-year retention. | Finance / Compliance | Open — must resolve before Sprint 1 kickoff |
| OQ-003 | Is reversal restricted to internal service principals / finance roles, or callable by any authenticated user? | Product / Finance | Open — must resolve before Sprint 2 kickoff |
| OQ-004 | Is synchronous fraud hold required before GA? Would require a hold/release state machine on transfers. | Fraud / Product | Open — must resolve before Sprint 1 kickoff |
| OQ-005 | Cursor-based vs. offset-based pagination for transaction history at scale? | Platform Engineering / Product | Open — offset used in US-004; migrate if needed |
| OQ-006 | Multi-currency support within 12 months? Affects ledger data model currency column design. | Product Team | Open — deferred; single-currency assumed |
| OQ-007 | Reversal endpoint availability SLA — is brief unavailability during maintenance acceptable? | Finance / Platform | Open — before SDD authoring |
