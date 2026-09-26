# SRS: Payment Service

**Document ID:** SRS-20250115-001
**Version:** 1.0
**Status:** Draft
**Standard:** ISO/IEC/IEEE 29148:2018

---

## 1. Business Context

### 1.1 Background

The Payment Service is a core microservice within a Venmo-like peer-to-peer wallet and payments platform. It is responsible for the authoritative record of every money movement between user accounts. All fund transfers, balance inquiries, and transaction history within the platform are ultimately fulfilled by this service.

Double-entry bookkeeping is the foundational invariant: every credit to one account is matched by an equal and opposite debit to another. No monetary unit may be created, destroyed, or applied twice—regardless of concurrent activity, network retries, or partial failures.

### 1.2 Business Opportunity / Problem Statement

Peer-to-peer payment apps succeed or fail on trust. A single incident of a user's balance being incorrect—whether from a duplicate charge, a lost transfer, or a negative balance caused by a race condition—destroys that trust permanently. The Payment Service must therefore make financial correctness a hard guarantee, not a best-effort goal.

At the same time, the service must be operationally operable: support teams, finance auditors, and compliance officers must be able to trace every ledger mutation without ambiguity.

### 1.3 Business Objectives

| ID | Objective | Success Metric |
|---|---|---|
| BO-001 | Ensure every transfer moves money from exactly one source account to exactly one destination account with zero net creation or destruction of funds. | Zero discrepancy in daily reconciliation reports; net ledger balance delta = 0 for every batch. |
| BO-002 | Prevent duplicate transactions caused by client retries or network failures. | Idempotency key collision rate of 0 duplicate ledger entries in production. |
| BO-003 | Protect all accounts from concurrent-write anomalies (lost updates, negative balances). | Zero negative-balance incidents in production; zero lost-update incidents detected by audit. |
| BO-004 | Provide an immutable, auditable ledger that satisfies finance and compliance requirements. | 100% of ledger entries traceable to an originating request; audit reports generated on demand within SLA. |
| BO-005 | Deliver payment operations at a latency and throughput sufficient for a consumer-grade user experience. | p95 transfer latency ≤ 500 ms under normal load; service sustains ≥ 500 TPS. |

---

## 2. Stakeholders

| Stakeholder | Role | Interest | Influence |
|---|---|---|---|
| End Users (Senders) | Primary consumer — initiates transfers and views their own balance/history. | Money leaves their account correctly, once, and is reflected immediately. | High — direct product usage drives adoption. |
| End Users (Receivers) | Primary consumer — receives funds and views incoming transactions. | Money arrives promptly and in the exact amount sent. | High — direct product usage drives retention. |
| Finance / Compliance Team | Auditor / regulator liaison — runs reconciliation, responds to disputes. | Every mutation is recorded, immutable, and traceable; end-of-day books balance. | High — can block launch if auditability gates are not met. |
| Platform Engineering | Operator — deploys, monitors, and on-calls the service. | Service is observable, self-healing, and deployable with zero downtime. | High — owns production reliability. |
| Product Engineering | Builder — implements features against this SRS. | Clear, testable requirements with no ambiguity in edge cases. | Medium — consumer of this document. |
| External Payment Rails | Future integration — card networks, bank ACH (out of scope for this service). | Clean API boundary so future rail adapters can plug in without modifying core logic. | Low (currently) — informational only. |

---

## 3. Scope

### 3.1 In Scope

- **Transfer execution:** Debit source account and credit destination account atomically.
- **Idempotency:** Accept a client-supplied idempotency key; guarantee exactly-once execution.
- **Balance inquiry:** Return the current authoritative balance of a given account.
- **Transaction history:** Return a paginated, time-ordered list of ledger entries for a given account.
- **Double-entry ledger:** Persist every money movement as immutable debit/credit journal entries.
- **Concurrency control:** Prevent lost updates and negative balances under concurrent access.
- **Transaction status:** Allow callers to query the status of a previously submitted transfer.
- **Reversal / void:** Support reversing a completed transfer (creates offsetting journal entries; does not mutate originals).

### 3.2 Out of Scope

The following capabilities are handled by separate microservices and are explicitly excluded from this service:

- User registration, identity verification, and profile management (User Service).
- Authentication and JWT issuance/validation (Auth Service).
- Wallet top-up from external payment cards or bank accounts (Funding Service / future External Rail adapter).
- Push notifications and in-app alerts (Notification Service).
- Currency conversion and FX rate management.
- Fraud scoring and risk decisioning (Fraud Service — may call Payment Service, not the other way around).
- KYC / AML compliance screening.

### 3.3 System Context Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                          API Gateway / BFF                           │
│             (JWT validation, rate limiting, routing)                 │
└────────────────────────────┬─────────────────────────────────────────┘
                             │  REST (HTTPS)
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Payment Service                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │  Transfer API   │  │  Ledger Engine   │  │  History / Query  │  │
│  │  (REST)         │  │  (double-entry)  │  │  API (REST)       │  │
│  └────────┬────────┘  └────────┬─────────┘  └────────┬──────────┘  │
│           └───────────────────┼──────────────────────┘             │
│                               │                                     │
│                        ┌──────▼──────┐                             │
│                        │ PostgreSQL  │                             │
│                        │  (ledger,   │                             │
│                        │  accounts,  │                             │
│                        │  idempotency│                             │
│                        │  keys)      │                             │
│                        └─────────────┘                             │
└──────────────────────────────────────────────────────────────────────┘
         │                                              │
         │ publishes events                             │ reads account
         ▼                                              ▼
  ┌─────────────┐                             ┌─────────────────┐
  │  Message    │                             │  Fraud Service  │
  │  Broker     │                             │  (read-only,    │
  │  (future)   │                             │   advisory)     │
  └─────────────┘                             └─────────────────┘
```

---

## 4. Functional Requirements

### 4.1 Transfer Execution

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-001 | The system SHALL atomically debit the sender's account and credit the receiver's account for the exact transfer amount in a single database transaction. | Must Have | BO-001 |
| REQ-F-002 | The system SHALL reject a transfer request if the sender's current balance is insufficient to cover the transfer amount. | Must Have | BO-001, End Users |
| REQ-F-003 | The system SHALL reject a transfer request if the transfer amount is zero or negative. | Must Have | BO-001 |
| REQ-F-004 | The system SHALL reject a transfer request if the sender and receiver account IDs are identical. | Must Have | BO-001 |
| REQ-F-005 | The system SHALL record the transfer amount with full precision to avoid rounding loss (monetary amounts stored as integer minor currency units, e.g. cents). | Must Have | BO-001, Finance/Compliance |
| REQ-F-006 | The system SHALL return a unique, system-generated transaction ID upon successful transfer execution. | Must Have | End Users, Platform Engineering |

### 4.2 Idempotency

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-007 | The system SHALL accept a client-supplied idempotency key (UUID) on every transfer request. | Must Have | BO-002 |
| REQ-F-008 | The system SHALL, upon receiving a transfer request with an idempotency key that matches a previously completed transfer, return the original response without executing a second transfer. | Must Have | BO-002 |
| REQ-F-009 | The system SHALL, upon receiving a transfer request with an idempotency key that matches an in-progress transfer, return HTTP 409 Conflict until the in-progress transfer either completes or fails. | Must Have | BO-002 |
| REQ-F-010 | The system SHALL reject a transfer request where an idempotency key is reused with a different request payload (different amount, sender, or receiver), returning HTTP 422 Unprocessable Entity. | Must Have | BO-002 |
| REQ-F-011 | The system SHALL persist idempotency key records for a minimum of 24 hours. | Should Have | BO-002, Finance/Compliance |

### 4.3 Concurrency and Balance Safety

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-012 | The system SHALL acquire pessimistic row-level locks on the sender and receiver account rows (in a consistent ordering to avoid deadlocks) before modifying balances. | Must Have | BO-003 |
| REQ-F-013 | The system SHALL enforce a database-level CHECK constraint ensuring account balances never fall below zero. | Must Have | BO-003 |
| REQ-F-014 | The system SHALL handle deadlock exceptions by retrying the transaction up to three times with exponential backoff before returning an error to the caller. | Must Have | BO-003 |

### 4.4 Double-Entry Ledger

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-015 | The system SHALL persist every balance mutation as an immutable ledger entry containing: account ID, entry type (DEBIT/CREDIT), amount, currency, timestamp, transaction ID, and initiating user ID. | Must Have | BO-004, Finance/Compliance |
| REQ-F-016 | The system SHALL never update or delete a committed ledger entry; corrections SHALL be recorded as new offsetting entries referencing the original entry ID. | Must Have | BO-004, Finance/Compliance |
| REQ-F-017 | The system SHALL ensure that for every transfer transaction, the sum of all CREDIT entries equals the sum of all DEBIT entries (net zero). | Must Have | BO-001, BO-004 |

### 4.5 Balance Inquiry

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-018 | The system SHALL return the current authoritative balance for a given account ID. | Must Have | End Users |
| REQ-F-019 | The system SHALL return the balance as of a specified point-in-time timestamp when requested. | Should Have | Finance/Compliance |

### 4.6 Transaction History

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-020 | The system SHALL return a paginated list of ledger entries for a given account ID, ordered by entry timestamp descending by default. | Must Have | End Users |
| REQ-F-021 | The system SHALL support filtering transaction history by date range. | Should Have | End Users, Finance/Compliance |
| REQ-F-022 | The system SHALL support filtering transaction history by transaction type (DEBIT or CREDIT). | Could Have | End Users |

### 4.7 Transaction Status

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-023 | The system SHALL expose an endpoint to retrieve the status and details of a transfer by its transaction ID. | Must Have | End Users, Platform Engineering |
| REQ-F-024 | The system SHALL model transfer status as one of: PENDING, COMPLETED, FAILED, REVERSED. | Must Have | End Users, Finance/Compliance |

### 4.8 Transfer Reversal

| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-025 | The system SHALL support reversing a COMPLETED transfer by creating offsetting journal entries (credit original sender, debit original receiver) in a new atomic transaction. | Must Have | Finance/Compliance |
| REQ-F-026 | The system SHALL reject a reversal request if the target transfer is already in REVERSED or FAILED status. | Must Have | Finance/Compliance |
| REQ-F-027 | The system SHALL link the reversal transaction to the original transaction ID in the ledger. | Must Have | BO-004, Finance/Compliance |

---

## 5. User Stories and Acceptance Criteria

### US-001: Initiate a Transfer

**Story:** As a sender, I want to transfer a specified amount to another user so that the funds move from my account to theirs immediately and I receive confirmation.

**Linked REQ-IDs:** REQ-F-001, REQ-F-002, REQ-F-003, REQ-F-004, REQ-F-005, REQ-F-006, REQ-F-015, REQ-F-017

#### Scenario 1: Successful transfer with sufficient balance

**Given** the sender has an account with a balance of 100.00 USD (10000 cents)
**And** the receiver has a valid account
**And** the request includes a unique idempotency key
**When** the sender submits a transfer request for 25.00 USD (2500 cents) to the receiver
**Then** the sender's balance is decremented by 2500 cents
**And** the receiver's balance is incremented by 2500 cents
**And** two immutable ledger entries are created (one DEBIT, one CREDIT) summing to net zero
**And** the API returns HTTP 201 Created with a unique transaction ID and status COMPLETED

#### Scenario 2: Transfer rejected due to insufficient balance

**Given** the sender has a balance of 10.00 USD (1000 cents)
**When** the sender submits a transfer request for 50.00 USD (5000 cents)
**Then** no ledger entries are created
**And** no balance is changed
**And** the API returns HTTP 422 Unprocessable Entity with error code INSUFFICIENT_FUNDS

#### Scenario 3: Transfer rejected — zero or negative amount

**Given** the sender has a valid account with positive balance
**When** the sender submits a transfer request with amount 0 cents
**Then** the API returns HTTP 400 Bad Request with error code INVALID_AMOUNT
**And** no ledger entries are created

#### Scenario 4: Transfer rejected — sender equals receiver

**Given** a valid sender account
**When** the sender submits a transfer to their own account ID
**Then** the API returns HTTP 422 Unprocessable Entity with error code SELF_TRANSFER_NOT_ALLOWED
**And** no ledger entries are created

---

### US-002: Retry a Transfer Safely

**Story:** As a sender whose network connection dropped, I want to retry my transfer request using the same idempotency key so that my money is only moved once even if the request is sent multiple times.

**Linked REQ-IDs:** REQ-F-007, REQ-F-008, REQ-F-009, REQ-F-010, REQ-F-011

#### Scenario 1: Retry after completed transfer returns original response

**Given** a transfer was successfully completed with idempotency key "idk-abc-123"
**When** the same request (same idempotency key, same payload) is submitted again
**Then** the API returns HTTP 200 OK with the original transaction ID and status COMPLETED
**And** no new ledger entries are created
**And** no balances are changed

#### Scenario 2: Retry while transfer is in-progress returns 409

**Given** a transfer with idempotency key "idk-xyz-456" is currently being processed
**When** a second request arrives with the same idempotency key before the first completes
**Then** the API returns HTTP 409 Conflict with error code TRANSFER_IN_PROGRESS

#### Scenario 3: Idempotency key reused with different payload is rejected

**Given** a transfer was successfully completed with idempotency key "idk-def-789" for 100 cents
**When** a new request arrives with the same idempotency key but for 200 cents
**Then** the API returns HTTP 422 Unprocessable Entity with error code IDEMPOTENCY_KEY_CONFLICT
**And** no new ledger entries are created

---

### US-003: View My Current Balance

**Story:** As a user, I want to see my current account balance so that I know how much money I have available before making a transfer.

**Linked REQ-IDs:** REQ-F-018, REQ-F-019

#### Scenario 1: Balance inquiry returns current balance

**Given** the user has an account with a balance of 7500 cents
**When** the user calls GET /accounts/{accountId}/balance
**Then** the API returns HTTP 200 OK with balance: 7500, currency: "USD"

#### Scenario 2: Balance inquiry for non-existent account

**Given** the account ID does not exist in the system
**When** the user calls GET /accounts/{accountId}/balance
**Then** the API returns HTTP 404 Not Found with error code ACCOUNT_NOT_FOUND

#### Scenario 3: Point-in-time balance inquiry

**Given** the user had a balance of 5000 cents at 2024-01-01T00:00:00Z and it has since changed
**When** the user calls GET /accounts/{accountId}/balance?asOf=2024-01-01T00:00:00Z
**Then** the API returns HTTP 200 OK with balance: 5000 as of that timestamp

---

### US-004: View Transaction History

**Story:** As a user, I want to view my payment history so that I can see what was sent and received and resolve any disputes.

**Linked REQ-IDs:** REQ-F-020, REQ-F-021, REQ-F-022

#### Scenario 1: Paginated transaction history returned successfully

**Given** the account has 50 ledger entries
**When** the user calls GET /accounts/{accountId}/transactions?page=1&pageSize=20
**Then** the API returns HTTP 200 OK with 20 entries, sorted by timestamp descending
**And** the response includes pagination metadata (totalCount: 50, page: 1, pageSize: 20, totalPages: 3)

#### Scenario 2: Date-range filtered history

**Given** the account has transactions spanning several months
**When** the user calls GET /accounts/{accountId}/transactions?from=2024-01-01&to=2024-01-31
**Then** only entries with timestamps within January 2024 are returned
**And** entries outside the date range are excluded

#### Scenario 3: Empty history for new account

**Given** the account exists but has no transactions
**When** the user calls GET /accounts/{accountId}/transactions
**Then** the API returns HTTP 200 OK with an empty list and totalCount: 0

---

### US-005: Check Transfer Status

**Story:** As a sender, I want to check the status of a transfer I submitted so that I know whether it completed, is pending, or failed.

**Linked REQ-IDs:** REQ-F-023, REQ-F-024

#### Scenario 1: Query a completed transfer

**Given** a transfer with ID "txn-001" completed successfully
**When** the caller calls GET /transfers/{transactionId}
**Then** the API returns HTTP 200 OK with status: COMPLETED, amount, sender ID, receiver ID, and timestamp

#### Scenario 2: Query a non-existent transfer

**Given** no transfer with ID "txn-999" exists
**When** the caller calls GET /transfers/txn-999
**Then** the API returns HTTP 404 Not Found with error code TRANSACTION_NOT_FOUND

---

### US-006: Reverse a Completed Transfer

**Story:** As a finance team member, I want to reverse a completed transfer so that an erroneous payment can be corrected without mutating the original ledger entries.

**Linked REQ-IDs:** REQ-F-025, REQ-F-026, REQ-F-027, REQ-F-015, REQ-F-016, REQ-F-017

#### Scenario 1: Successful reversal of a completed transfer

**Given** transfer "txn-100" is in COMPLETED status
**And** the original receiver has sufficient balance to support the reversal
**When** an authorised caller submits POST /transfers/txn-100/reverse
**Then** the original sender's balance is incremented by the original transfer amount
**And** the original receiver's balance is decremented by the original transfer amount
**And** two new offsetting ledger entries are created referencing "txn-100"
**And** transfer "txn-100" status is updated to REVERSED
**And** the API returns HTTP 201 Created with the new reversal transaction ID

#### Scenario 2: Reversal rejected — transfer already reversed

**Given** transfer "txn-200" is already in REVERSED status
**When** an authorised caller submits POST /transfers/txn-200/reverse
**Then** the API returns HTTP 422 Unprocessable Entity with error code TRANSFER_ALREADY_REVERSED
**And** no ledger entries are created

#### Scenario 3: Reversal rejected — receiver has insufficient balance

**Given** transfer "txn-300" is COMPLETED and the original receiver has since spent the funds
**When** an authorised caller submits POST /transfers/txn-300/reverse
**Then** the API returns HTTP 422 Unprocessable Entity with error code INSUFFICIENT_FUNDS_FOR_REVERSAL
**And** no ledger entries are created
**And** no balances are changed

---

### US-007: Concurrent Transfers Do Not Corrupt Balances

**Story:** As a platform engineer, I want the payment service to safely handle concurrent transfers involving the same account so that no balance is lost or duplicated under high concurrency.

**Linked REQ-IDs:** REQ-F-012, REQ-F-013, REQ-F-014

#### Scenario 1: Two concurrent debits — only one succeeds when balance allows only one

**Given** account A has a balance of 1000 cents
**And** two concurrent requests each attempt to debit 800 cents from account A
**When** both requests are processed simultaneously
**Then** exactly one request succeeds and decrements the balance to 200 cents
**And** the other request fails with INSUFFICIENT_FUNDS
**And** the final balance is exactly 200 cents (no money destroyed or created)

#### Scenario 2: Deadlock retry succeeds within retry budget

**Given** a deadlock occurs on the first attempt of a transfer
**When** the system retries with exponential backoff
**Then** the transfer completes successfully within 3 retry attempts
**And** only one set of ledger entries is created (idempotency is maintained during retries)

---

## 6. Non-Functional Requirements (ISO/IEC 25010)

| REQ-ID | Quality Characteristic | Sub-characteristic | Description | Measure / Target |
|---|---|---|---|---|
| REQ-NF-001 | Performance Efficiency | Time Behaviour | Transfer API (POST /transfers) end-to-end latency under normal load. | p50 ≤ 150 ms, p95 ≤ 500 ms, p99 ≤ 1000 ms |
| REQ-NF-002 | Performance Efficiency | Time Behaviour | Balance inquiry (GET /accounts/{id}/balance) latency. | p95 ≤ 100 ms |
| REQ-NF-003 | Performance Efficiency | Capacity | Sustained throughput for transfer requests. | ≥ 500 TPS at p95 ≤ 500 ms with ≤ 0.1% error rate |
| REQ-NF-004 | Performance Efficiency | Resource Utilisation | Database connection pool utilisation under peak load. | ≤ 80% pool utilisation at 500 TPS |
| REQ-NF-005 | Reliability | Maturity | Service availability. | ≥ 99.9% monthly uptime (≤ 43.8 min downtime/month) |
| REQ-NF-006 | Reliability | Fault Tolerance | Service remains operational when one of two PostgreSQL replicas is unavailable. | Zero data loss; reads degrade to primary only within 10 s |
| REQ-NF-007 | Reliability | Recoverability | Recovery time after a service crash with no in-flight transactions. | RTO ≤ 60 s; RPO = 0 (no committed transaction data lost) |
| REQ-NF-008 | Functional Suitability | Functional Correctness | Net ledger balance delta per reconciliation batch must be exactly zero. | 0 discrepancy in 100% of daily reconciliation runs |
| REQ-NF-009 | Security | Integrity | No transfer may mutate an account balance without a corresponding immutable ledger entry. | 100% of balance mutations traceable to a ledger entry in audit log |
| REQ-NF-010 | Security | Non-repudiation | All ledger entries include initiating user ID and request timestamp. | 100% of ledger entries contain userId, requestTimestamp, transactionId |
| REQ-NF-011 | Security | Confidentiality | Account balances and transaction details are accessible only to the owning user or authorised service principals (enforced at API Gateway, verified at service boundary). | 0 unauthorised balance or transaction disclosures in penetration test |
| REQ-NF-012 | Maintainability | Analysability | Distributed trace (OpenTelemetry) spans emitted for every inbound request and every database write. | 100% of requests produce a complete trace; trace visible in observability platform within 30 s |
| REQ-NF-013 | Maintainability | Testability | Unit and integration test coverage of core transfer and ledger logic. | ≥ 90% line coverage on transfer and ledger packages |
| REQ-NF-014 | Maintainability | Modifiability | The service exposes a versioned REST API (URI versioning, e.g. /v1/transfers) to allow non-breaking evolution. | New API versions deployed without removing prior version for ≥ 6 months |
| REQ-NF-015 | Reliability | Maturity | Idempotency key store must survive a service restart without loss. | Idempotency key records durable in PostgreSQL; survive pod restart with 0 loss |
| REQ-NF-016 | Performance Efficiency | Time Behaviour | Transaction history query (paginated, first page) latency. | p95 ≤ 200 ms for page size ≤ 50 entries |
| REQ-NF-017 | Portability | Adaptability | The service is deployable as a Docker container orchestrated by Kubernetes with no host-specific dependencies. | Passes containerised integration test suite with zero host-specific configuration |
| REQ-NF-018 | Compatibility | Interoperability | The service API follows OpenAPI 3.x specification; a machine-readable spec is published at /v1/openapi.json. | API spec validates with no errors against OpenAPI 3.x validator |

---

## 7. Assumptions

| ID | Assumption | Impact if Wrong |
|---|---|---|
| ASM-001 | The API Gateway validates JWT tokens and forwards the authenticated user ID as a trusted header (X-User-Id) to this service. | Payment Service would need to implement its own JWT validation, increasing scope and security surface. |
| ASM-002 | All monetary amounts are denominated in a single currency (USD) for the initial release; multi-currency support is not required. | Currency conversion logic would need to be added; amount storage model may need a currency column and FX rate tracking. |
| ASM-003 | Account records (account ID, owner user ID) are created and managed by a separate Account Service and exist in this service's database as a foreign-key reference only. | If accounts are managed here, user registration flows enter scope. |
| ASM-004 | PostgreSQL is the sole persistent store; no external cache (e.g. Redis) is required to meet the stated latency SLAs at launch. | Caching layer would be needed, adding operational complexity and cache-invalidation correctness concerns. |
| ASM-005 | The maximum transfer amount in a single transaction does not exceed the platform's per-transaction limit defined by the Product team (exact value TBD — see OQ-001). | If no limit exists, the service must handle extremely large transfers without integer overflow in the minor-unit representation. |
| ASM-006 | The Fraud Service is a read-only, advisory consumer; it does not block or veto transfers synchronously in this release. | If fraud holds are required synchronously, a hold/release lifecycle must be added to the transfer state machine. |
| ASM-007 | Client-supplied idempotency keys are UUIDs (version 4); the service does not generate them on behalf of clients. | If the service must generate idempotency keys, the API contract and retry semantics change. |

---

## 8. Constraints

| ID | Constraint | Type | Rationale |
|---|---|---|---|
| CON-001 | Implementation language is Java with Spring Boot. | Technical | Mandated by platform engineering's standardised service template. |
| CON-002 | Persistence layer is PostgreSQL (version 14+). | Technical | Mandated by platform data strategy; required for SERIALIZABLE or SELECT FOR UPDATE concurrency guarantees. |
| CON-003 | Monetary amounts MUST be stored as integer minor currency units (e.g. cents for USD) — no floating-point types in ledger columns. | Technical / Financial | Floating-point arithmetic cannot represent all decimal currency values exactly; any rounding loss violates BO-001. |
| CON-004 | The service MUST be stateless at the application tier; all durable state is in PostgreSQL. | Technical | Required for horizontal scaling and zero-downtime rolling deployments. |
| CON-005 | The REST API MUST follow the JSON:API or a project-standard envelope format consistently across all endpoints. | Technical | Interoperability with other microservices and the API Gateway. |
| CON-006 | All database schema changes MUST be applied via Liquibase migration scripts; no ad-hoc DDL in production. | Technical / Operational | Ensures reproducibility across environments and supports rollback. |
| CON-007 | The service MUST emit structured JSON logs (no plaintext) to stdout, consumable by the platform log aggregation pipeline. | Operational | Platform observability tooling requires structured logs for alerting and dashboards. |
| CON-008 | The service MUST expose a /health/liveness and /health/readiness endpoint compatible with Kubernetes probes. | Technical | Required by the container orchestration platform for traffic management and restart policies. |
| CON-009 | No external payment rail integrations in this service's scope. Any future rail adapter is a separate service with its own SRS. | Business | Agreed scope boundary with stakeholders. |

---

## 9. Open Questions

| ID | Question | Owner | Target Date |
|---|---|---|---|
| OQ-001 | What is the maximum single-transaction transfer amount (per-transaction limit)? This affects integer overflow safety planning and product policy. | Product Team | Before Sprint 1 kickoff |
| OQ-002 | What is the required idempotency key retention period beyond the initial 24-hour minimum? Finance/compliance may require 90-day or 7-year retention for audit. | Finance / Compliance | Before Sprint 1 kickoff |
| OQ-003 | Should the reversal operation be callable by any authenticated user (e.g. the original sender), or restricted to internal service principals and finance team roles only? | Product Team / Finance | Before Sprint 2 kickoff |
| OQ-004 | Is a synchronous fraud-hold mechanism required before GA, or will fraud intervention remain asynchronous / post-hoc for the initial release? | Fraud / Product Team | Before Sprint 1 kickoff |
| OQ-005 | What pagination strategy should be used for transaction history at scale — offset-based (simple but slow for large offsets) or cursor-based (keyset pagination)? | Platform Engineering / Product | Before technical design review |
| OQ-006 | Is multi-currency support required within 12 months of launch? The answer affects the data model for ledger entries (currency column, FX rate reference). | Product Team | Before SDD authoring |
| OQ-007 | What SLA governs the availability of the reversal endpoint specifically — is it acceptable for reversals to be unavailable during a short maintenance window if transfers remain available? | Finance / Platform Engineering | Before SDD authoring |

---

## 10. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2025-01-15 | Product Owner Agent | Initial draft — full ISO/IEC/IEEE 29148:2018 SRS for Payment Service. |
