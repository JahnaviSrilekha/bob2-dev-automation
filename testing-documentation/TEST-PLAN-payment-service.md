# Test Plan: Payment Service

**Document ID:** TP-20250115-001  
**Version:** 1.0  
**Status:** Draft  
**Standard:** ISO/IEC/IEEE 29119-3:2021  
**SRS Reference:** SRS-20250115-001  
**SDD Reference:** SDD-20250115-001  
**Backlog Reference:** BACKLOG-payment-service.md  

---

## 1. Introduction

### 1.1 Scope

This Test Plan governs all testing activities for the **Payment Service** — a core microservice responsible for authoritative money movement in a peer-to-peer payments platform. The service provides transfer execution, double-entry ledger persistence, idempotency enforcement, balance inquiry, transaction history, transaction status queries, and transfer reversal.

Testing spans four levels: **Unit**, **Integration**, **System (contract)**, and **Acceptance**, covering all 27 functional requirements (REQ-F-001 through REQ-F-027) and 18 non-functional requirements (REQ-NF-001 through REQ-NF-018).

### 1.2 Out of Scope

The following are explicitly excluded from this test plan:

| Excluded Area | Reason |
|---|---|
| User registration and identity management | Handled by User Service |
| JWT issuance and validation | Handled by Auth Service |
| Wallet top-up from external payment rails | Handled by Funding Service |
| Push notifications | Handled by Notification Service |
| Currency conversion and FX rate management | Out of scope per SRS §3.2 |
| Fraud scoring and risk decisioning | Handled by Fraud Service |
| KYC / AML compliance screening | Out of scope per SRS §3.2 |
| Performance load testing (at scale) | Deferred to separate performance test campaign |
| Security penetration testing | Deferred to separate security engagement |

### 1.3 References

| Document | Version | Location |
|---|---|---|
| SRS-20250115-001 | 1.0 | `product-documentation/SRS-payment-service.md` |
| SDD-20250115-001 | 1.0 | `technical-documentation/SDD-payment-service.md` |
| BACKLOG-payment-service | 1.0 | `delivery-documentation/BACKLOG-payment-service.md` |
| ISO/IEC/IEEE 29119-3:2021 | — | Test Documentation standard |
| ISO/IEC 25010 | — | Quality characteristics (NFRs) |
| JUnit 5 Reference | 5.x | https://junit.org/junit5/docs/current/user-guide/ |
| Testcontainers Reference | 1.19+ | https://testcontainers.com |
| Cucumber 7 Reference | 7.x | https://cucumber.io/docs |

---

## 2. Test Items

The following software components are under test:

| Component | Layer | Key Classes | Linked REQ-IDs |
|---|---|---|---|
| Transfer API controller | API | `TransferController` | REQ-F-001–006, REQ-F-007–010 |
| Transfer service (business logic) | Service | `TransferService`, `IdempotencyService` | REQ-F-001–014 |
| Ledger service (double-entry) | Service | `LedgerService` | REQ-F-015–017 |
| Balance inquiry API | API + Service | `AccountController`, `BalanceService` | REQ-F-018–019 |
| Transaction history API | API + Service | `AccountController`, `HistoryService` | REQ-F-020–022 |
| Transaction status API | API + Service | `TransferController`, `TransferQueryService` | REQ-F-023–024 |
| Reversal service | Service | `ReversalService` | REQ-F-025–027 |
| Idempotency gate | Service + DB | `IdempotencyService`, `IdempotencyKeyRepository` | REQ-F-007–011 |
| Concurrency / locking | Service + DB | `AccountRepository.findByIdForUpdate()`, `@Retryable` | REQ-F-012–014 |
| Data model + constraints | DB | Liquibase migrations, `accounts`, `ledger_entries`, `transactions`, `idempotency_keys` | REQ-F-005, REQ-F-013, REQ-F-015–016 |

---

## 3. Features to Be Tested

| Feature | Test Level | Technique(s) | Priority |
|---|---|---|---|
| Transfer execution (happy path) | Integration, Acceptance | EP, BVA | Critical |
| Transfer rejection — insufficient funds | Unit, Integration | EP, BVA | Critical |
| Transfer rejection — zero / negative amount | Unit, Integration | EP, BVA | Critical |
| Transfer rejection — self-transfer | Unit, Integration | DT | High |
| Transfer rejection — missing Idempotency-Key | Unit, Integration | DT | High |
| Double-entry ledger persistence | Integration | EP, DT | Critical |
| Net-zero ledger invariant (DB assertion) | Integration | DT | Critical |
| Ledger immutability (no UPDATE/DELETE) | Integration | DT | Critical |
| Idempotency — replay of completed transfer | Integration, Acceptance | DT | Critical |
| Idempotency — in-progress transfer (409) | Integration | DT | Critical |
| Idempotency — key conflict different payload (422) | Integration | DT | Critical |
| Idempotency key TTL and persistence across restart | Integration | DT | High |
| Concurrent transfers — race condition safety | Integration | EP | Critical |
| Deadlock retry with exponential backoff | Integration | STT | Critical |
| Deadlock exhaustion → HTTP 503 | Integration | STT | High |
| Balance inquiry (current) | Unit, Integration | EP, BVA | High |
| Balance inquiry (point-in-time) | Integration | EP | Medium |
| Balance inquiry — account not found | Unit, Integration | EP | High |
| Transaction history (pagination) | Integration | BVA | High |
| Transaction history (date filter) | Integration | EP | Medium |
| Transaction history (type filter) | Integration | EP | Medium |
| Transaction status (found / not found) | Integration | EP | High |
| Transfer state machine (PENDING→COMPLETED/FAILED/REVERSED) | Integration, Acceptance | STT | Critical |
| Reversal — successful | Integration, Acceptance | DT, STT | Critical |
| Reversal — already reversed (422) | Integration | STT | High |
| Reversal — receiver insufficient funds | Integration | DT | High |
| BigDecimal arithmetic precision (0.1+0.2, penny splits) | Unit | BVA | Critical |
| Accounts balance CHECK constraint (DB level) | Integration | BVA | Critical |
| Health and readiness endpoints | Integration | EP | Medium |
| OpenAPI spec availability | Integration | EP | Low |

---

## 4. Features Not to Be Tested

| Feature | Justification |
|---|---|
| JWT validation | Handled by API Gateway (ASM-001); mocked as trusted header `X-User-Id` in tests |
| Multi-currency FX conversion | Out of scope for initial release (ASM-002) |
| RabbitMQ event delivery latency | Infrastructure concern; message publishing is fire-and-forget after DB commit |
| Kubernetes pod auto-scaling behaviour | Platform infrastructure, not application logic |
| External payment rail adapters | Out of scope (CON-009) |
| Performance benchmark at 500 TPS | Deferred to dedicated performance test campaign (REQ-NF-001 to REQ-NF-004) |
| Security penetration testing | Deferred to dedicated security engagement (REQ-NF-011) |

---

## 5. Test Approach

### 5.1 Test Levels

#### Unit Tests
- **Objective:** Verify individual service and utility class logic in complete isolation.
- **Techniques:** EP, BVA, DT
- **Tools:** JUnit 5, Mockito, AssertJ
- **Scope:** `TransferService`, `LedgerService`, `IdempotencyService`, `ReversalService`, `BigDecimal` arithmetic helpers
- **Key patterns:** `@ExtendWith(MockitoExtension.class)`, all dependencies mocked, no Spring context, no DB

#### Integration Tests
- **Objective:** Verify that components work correctly with a real PostgreSQL database, and that all DB constraints, transactions, and queries behave as specified.
- **Techniques:** EP, BVA, DT, STT
- **Tools:** JUnit 5, Spring Boot Test (`@SpringBootTest`), Testcontainers (PostgreSQL), AssertJ, `JdbcTemplate` for DB-level assertions
- **Key patterns:** `@Testcontainers`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, shared Testcontainer lifecycle, `@Transactional` rollback for state isolation
- **Critical patterns:** After every transfer, assert `SELECT SUM(CASE entry_type WHEN 'CREDIT' THEN amount WHEN 'DEBIT' THEN -amount END) FROM ledger_entries WHERE transaction_id = ?` = 0

#### System (Contract) Tests
- **Objective:** Verify the published REST API contract matches the OpenAPI specification; verify error envelope schema consistency.
- **Techniques:** EP, DT
- **Tools:** Spring Boot Test + MockMvc, AssertJ, OpenAPI validator
- **Key patterns:** Test every endpoint against its documented response schema; verify `errorCode`, `message`, `traceId` fields are present on all error responses

#### Acceptance Tests
- **Objective:** Verify all user story acceptance criteria pass end-to-end.
- **Techniques:** Gherkin (BDD)
- **Tools:** Cucumber 7, JUnit 5 Cucumber runner, Spring Boot Test, Testcontainers
- **Key patterns:** `@CucumberContextConfiguration`, `@SpringBootTest`, feature files tagged with `@REQ-F-NNN @TC-NNN`

### 5.2 Test Design Techniques

| Technique | Application |
|---|---|
| **Equivalence Partitioning (EP)** | Transfer amount partitions (valid positive, zero, negative, above-balance, exact-balance), account existence (found, not found), idempotency key states (new, completed, in-progress, conflicting) |
| **Boundary Value Analysis (BVA)** | Amount boundaries (−1, 0, 1, balance−1, balance, balance+1 cents), pagination (page=0, page=1, last page, beyond last page), pageSize (0, 1, 50, 100, 101) |
| **Decision Tables (DT)** | Transfer validation matrix (amount valid × balance sufficient × sender≠receiver × key state), idempotency dispatch matrix, reversal eligibility matrix |
| **State Transition Testing (STT)** | Transaction lifecycle: PENDING → COMPLETED, PENDING → FAILED, COMPLETED → REVERSED; deadlock retry state machine; idempotency key states: PENDING → COMPLETED / FAILED |

---

## 6. Entry and Exit Criteria

| Level | Entry Criteria | Exit Criteria |
|---|---|---|
| Unit | Source code compiles; Liquibase migration files authored | ≥ 90% line coverage on `transfer` and `ledger` packages (REQ-NF-013); 0 failing tests; 0 compilation warnings treated as errors |
| Integration | Unit tests pass; Testcontainers available in CI; PostgreSQL 14+ image accessible | All API contract tests pass; DB-level net-zero assertion passes for every transfer integration test; 0 Critical or High defects open |
| System / Contract | Integration tests pass; OpenAPI spec generated | All endpoint responses match OpenAPI schema; all error envelopes contain `errorCode`, `message`, `traceId` |
| Acceptance | All lower-level tests pass; Cucumber runner configured | 100% of US acceptance criteria scenarios pass (`@wip` tag absent from all scenarios); RTM shows 0 uncovered REQ-F rows |

---

## 7. Test Environment

| Component | Version / Value | Notes |
|---|---|---|
| JDK | 17 (LTS) | Matches platform standard (CON-001) |
| Spring Boot | 3.x | Service framework (CON-001) |
| JUnit 5 | 5.10+ | Test runner |
| Mockito | 5.x | Mocking framework for unit tests |
| AssertJ | 3.x | Fluent assertion library |
| Testcontainers (PostgreSQL) | 1.19+ | Real PostgreSQL instance for integration tests |
| PostgreSQL | 14+ | Matches production constraint (CON-002) |
| Cucumber 7 | 7.x | BDD / acceptance test runner |
| Maven / Gradle | Project standard | Build and test execution |
| CI/CD | GitHub Actions / Jenkins | Automated test execution on every PR |

**Test data:** All test data is generated programmatically within tests. No production data is used. Account IDs are random UUIDs per test; balances are set to known values in `@BeforeEach` setup. Testcontainers resets schema state via `@Transactional` rollback or table truncation after each test class.

---

## 8. Test Data Strategy

| Data Category | Generation Approach | Notes |
|---|---|---|
| Account IDs | `UUID.randomUUID()` in `@BeforeEach` | Ensures test isolation; no cross-test collisions |
| Initial balances | Inserted directly via `JdbcTemplate` or Spring Data repository in `@BeforeEach` | Known values (e.g. 10000, 1000, 0) matched to BVA boundaries |
| Idempotency keys | `UUID.randomUUID().toString()` per test | New key per scenario; reuse modelled explicitly in idempotency tests |
| Transfer amounts | Exact BigDecimal string literals (`"25.0000"`, `"0.1000"`, `"0.3330"`) | Never use double/float literals (CON-003) |
| Transaction IDs | System-generated; captured from response and stored in test context | Used for status query and reversal tests |
| Ledger assertions | SQL via `JdbcTemplate` post-transfer | Net-zero query run after every integration transfer test |

**Anonymisation:** Not applicable — no production data is used.

---

## 9. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| Testcontainer startup flakiness in CI | High — test suite can't run | Low | Pin PostgreSQL image version; use `@Container` static lifecycle; add retry on container start |
| Concurrent test timing sensitivity | High — race tests non-deterministic | Medium | Use `CountDownLatch` + `ExecutorService` with fixed thread pool; `@RepeatedTest(20)` to amplify probability of catching races |
| Idempotency in-progress (409) is hard to reproduce deterministically | High — key correctness requirement | Medium | Use DB transaction isolation: pause first request mid-transaction via advisory lock in test, then fire second request |
| Per-transaction limit undefined (OQ-001) | Medium — BVA upper boundary unknown | High | Use `Long.MAX_VALUE / 100` cents as proxy max; annotate test with `@Disabled("OQ-001: per-transaction limit TBD")` |
| Reversal access control undefined (OQ-003) | Low — auth is mocked | Medium | Tests pass `X-User-Id` header; access control logic deferred; add TODO in test for auth assertion |
| BigDecimal scale drift | Critical — financial corruption | Low | Assert `.scale() == 4` and use `compareTo(ZERO) == 0` for net-zero; never use `equals()` on BigDecimal across scales |

---

## 10. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2025-01-15 | Testing Engineer Agent | Initial draft — full ISO/IEC/IEEE 29119-3 Test Plan for Payment Service |
