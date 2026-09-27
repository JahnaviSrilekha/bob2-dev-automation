# Sprint Plan: Payment Service

**SRS Reference:** SRS-20250115-001
**Backlog Reference:** BACKLOG-payment-service.md
**Total stories:** 11  **Total points:** 57  **Sprints required:** 2
**Team velocity:** 40 points/sprint  **Sprint duration:** 2 weeks  **Team size:** 3 developers
**Sprint start date basis:** 2025-02-03 (adjust to actual kickoff date)

---

## Sprint Overview

| Sprint | Goal Summary | Points Committed | Stories |
|---|---|---|---|
| Sprint 1 | Core money movement is live: transfers execute atomically with double-entry ledger, idempotency prevents duplicates, concurrency is safe, and balance inquiry is available | 36 | US-008, US-001a, US-001b, US-007, US-002, US-003, US-005, US-010 |
| Sprint 2 | Full operational capability: transfers can be reversed, complete transaction history is queryable, admins can view all transactions, and the service is observable and production-ready | 21 | US-006, US-004, US-009, US-011 |

---

## Sprint Details

---

## Sprint 1 (03 Feb – 14 Feb 2025)

**Capacity:** 40 points  
**Committed:** 36 points  
**Remaining capacity:** 4 points (buffer for unplanned work / OQ-001 resolution)

| Story | Title | Points | Epic | Dependencies |
|---|---|---|---|---|
| US-008 | Database Schema & Liquibase Foundation | 3 | EPIC-001 | None |
| US-001a | Transfer API Endpoint & Input Validation | 5 | EPIC-001 | US-008 |
| US-001b | Double-Entry Ledger Persistence | 5 | EPIC-001 | US-008, US-001a (interface) |
| US-007 | Concurrency-Safe Balance Deduction | 5 | EPIC-003 | US-008, US-001b |
| US-002 | Idempotent Transfer Execution | 8 | EPIC-002 | US-008, US-001a, US-001b |
| US-003 | Balance Inquiry Endpoint | 5 | EPIC-004 | US-008 |
| US-005 | Transfer Status Endpoint | 3 | EPIC-006 | US-008, US-001b |
| US-010 | OpenAPI Spec & API Versioning | 2 | EPIC-001 | US-001a, US-003, US-005 |

**Sprint Goal:**  
By the end of Sprint 1, a caller can initiate a transfer that atomically debits the sender and credits the receiver with full double-entry ledger persistence, retries the same transfer safely via an idempotency key, query the current or point-in-time balance of any account, and check the status of any submitted transfer — all without risk of concurrent corruption or duplicate execution.

**Story sequencing within sprint (recommended dev order):**

| Day | Focus | Story |
|---|---|---|
| Day 1–2 | Schema foundation — all subsequent stories depend on it | US-008 |
| Day 2–4 | Transfer API + validation (can be developed in parallel with US-001b once interfaces are agreed) | US-001a |
| Day 3–5 | Ledger persistence wired into transfer service | US-001b |
| Day 5–7 | Concurrency safety layered on top of transfer service | US-007 |
| Day 6–8 | Idempotency key store and retry semantics (depends on transfer service being stable) | US-002 |
| Day 7–8 | Balance inquiry (independent read path — can proceed in parallel) | US-003 |
| Day 8–9 | Transfer status endpoint (simple read — can proceed in parallel) | US-005 |
| Day 9–10 | OpenAPI annotations + spec endpoint (applied after controllers exist) | US-010 |

**Risks / flags:**
- **OQ-001 (transfer amount limit)** must be resolved before Sprint 1 kickoff — without it, overflow safety check cannot be implemented in US-001a.
- **OQ-004 (fraud hold)** must be resolved before Sprint 1 kickoff — if synchronous fraud holds are required, US-001a scope changes significantly.
- **US-007 concurrency integration test** (Testcontainers concurrent threads) is the highest-effort test in the sprint (~4h) and carries medium risk; flag early if Testcontainers setup is problematic in CI.
- **US-002 in-progress detection** relies on DB unique constraint as the atomic gate — verify PostgreSQL unique constraint error code mapping in Spring Data JPA during US-002 implementation.

---

## Sprint 2 (17 Feb – 28 Feb 2025)

**Capacity:** 40 points
**Committed:** 21 points
**Remaining capacity:** 19 points

> **Note:** Sprint 2 remaining capacity (19 pts after US-011 addition) is reserved for:
> - OQ-002 resolution (idempotency key retention policy may require a schema migration and TTL update to US-002 cleanup job)
> - OQ-003 resolution (reversal authorisation — role-based access control on US-006 if required before GA)
> - OQ-005 resolution (cursor-based pagination migration for US-004 if product decides before Sprint 2 kickoff)
> - Any carry-over from Sprint 1 (idempotency concurrency edge case, deadlock retry tuning)
> - Integration test gap closure and coverage enforcement (REQ-NF-013: ≥ 90% line coverage)

| Story | Title | Points | Epic | Dependencies |
|---|---|---|---|---|
| US-006 | Reverse a Completed Transfer | 8 | EPIC-007 | US-008, US-001b, US-007 |
| US-004 | Paginated Transaction History | 5 | EPIC-005 | US-008, US-001b |
| US-009 | Service Observability & Health Endpoints | 3 | EPIC-001 | US-008 |
| US-011 | Admin Views All Transactions | 5 | EPIC-008 | US-008, US-001b |

**Sprint Goal:**
By the end of Sprint 2, the service is production-ready: finance team members can reverse completed transfers with full double-entry correctness, users can retrieve their complete paginated and filtered transaction history, admins can view all platform transactions with role-based access control, and the service emits structured logs, distributed traces, and Kubernetes health probes required for safe deployment.

**Story sequencing within sprint (recommended dev order):**

| Day | Focus | Story |
|---|---|---|
| Day 1–2 | Observability config (unblocks CI/production readiness early in sprint) | US-009 |
| Day 2–4 | Transaction history (independent read path; can be developed in parallel) | US-004 |
| Day 3–5 | Admin transaction view (independent read path; shares pagination pattern with US-004) | US-011 |
| Day 4–9 | Reversal (most complex story; requires US-001b + US-007 patterns) | US-006 |
| Day 9–10 | Integration test pass, coverage enforcement, OQ resolution items | Buffer |

**Risks / flags:**
- **OQ-003 (reversal authorisation)** must be resolved by Sprint 2 kickoff. If role-based restriction is required, US-006 adds an RBAC sub-task (~3h) that fits within the existing 8-point estimate if resolved early; if a new story is needed, it consumes buffer capacity.
- **US-006 receiver-insufficient-balance scenario** requires the same pessimistic locking path as US-007 — ensure lock ordering is consistently applied to prevent new deadlock patterns during reversal.
- **US-011 role guard**: the `X-User-Role` header is assumed trusted per ASM-001 (API Gateway forwards it); if the Gateway does not forward this header, an OQ must be raised to agree on the admin role claim mechanism before Sprint 2 kickoff.
- **Coverage gate (REQ-NF-013 ≥ 90%)**: if Sprint 1 coverage is below target, Day 9–10 buffer must be used for test gap closure before Sprint 2 demo.

---

## Backlog Items Not Yet Scheduled

Stories deferred to future sprints or pending prioritisation:

| Story | Title | Points | Reason Deferred |
|---|---|---|---|
| — | Idempotency key extended retention | TBD | Pending OQ-002 resolution (Finance/Compliance); may require schema migration to configurable TTL; currently hardcoded to 24h minimum in US-002 |
| — | Cursor-based pagination for transaction history | TBD | Pending OQ-005 resolution; offset-based pagination implemented in US-004; cursor migration is a follow-on Sprint 3 story if product confirms need |
| — | Reversal authorisation / RBAC | TBD | Pending OQ-003 resolution; assumed open to any authenticated caller for Sprint 2; role restriction is a Sprint 3 hardening story |
| — | Multi-currency ledger support | TBD | Pending OQ-006 resolution; assumed single-currency (USD) for launch; multi-currency requires data model changes to ledger_entries and a new FX rate concern |
| — | Fraud hold / release state machine | TBD | Pending OQ-004 resolution; if synchronous fraud holds are required before GA, this is a Sprint 3 Must Have story (estimated 8 pts) that would add HOLD/RELEASED states to the transaction status model |
| — | OpenTelemetry metrics dashboard + alerting rules | TBD | Operational maturity; not in SRS scope; recommended as Sprint 3 Platform Engineering story |

---

## Dependency Graph

```
US-008 (schema foundation)
    ├── US-001a (transfer API + validation)
    │       ├── US-002 (idempotency — wraps transfer)
    │       └── US-010 (OpenAPI annotations)
    ├── US-001b (ledger persistence)
    │       ├── US-001a (interface contract)
    │       ├── US-002 (idempotency — wraps transfer)
    │       ├── US-005 (transfer status — reads transactions table)
    │       ├── US-004 (history — reads ledger_entries table)
    │       ├── US-006 (reversal — extends ledger service)
    │       └── US-011 (admin view — reads transactions table cross-account)
    ├── US-007 (concurrency locking)
    │       └── US-006 (reversal — reuses lock ordering)
    ├── US-003 (balance inquiry — reads accounts table)
    └── US-009 (observability — runs on top of the application)
```

All Sprint 1 Must-Have priorities are unblocked within Sprint 1 by the US-008 → US-001a/US-001b → US-007/US-002 chain. No story crosses a sprint boundary.

---

## Points by Epic

| Epic | Stories | Points | Sprint |
|---|---|---|---|
| EPIC-001: Transfer Execution & Ledger Correctness | US-008, US-001a, US-001b, US-009, US-010 | 18 | Sprint 1 (US-008, US-001a, US-001b, US-010) + Sprint 2 (US-009) |
| EPIC-002: Idempotency & Exactly-Once Semantics | US-002 | 8 | Sprint 1 |
| EPIC-003: Concurrency Safety & Balance Protection | US-007 | 5 | Sprint 1 |
| EPIC-004: Balance Inquiry | US-003 | 5 | Sprint 1 |
| EPIC-005: Transaction History | US-004 | 5 | Sprint 2 |
| EPIC-006: Transaction Status | US-005 | 3 | Sprint 1 |
| EPIC-007: Transfer Reversal | US-006 | 8 | Sprint 2 |
| EPIC-008: Admin Transaction View | US-011 | 5 | Sprint 2 |
| **Total** | **11 stories** | **57 points** | **2 sprints** |

---

## Story Split Record

| Original Story | Reason for Split | Resulting Stories | Points Before | Points After |
|---|---|---|---|---|
| US-001 (Initiate a Transfer) | Estimated at 13 points — exceeds the hard 13+ split rule. The story spanned five distinct vertical concerns: REST controller design, input validation (4 rejection paths), double-entry ledger creation, net-zero enforcement, and transactional atomicity. Splitting at the API/validation boundary vs. the ledger/persistence boundary produces two independently deliverable, testable stories. | US-001a (Transfer API & Validation, 5 pts) + US-001b (Double-Entry Ledger Persistence, 5 pts) | 13 | 5 + 5 = 10 |
