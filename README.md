# bob2-dev-automation

A demonstration of **IBM Bob** as an end-to-end software development automation agent — from business requirements all the way to a running, tested microservice.

The project shows how Bob's specialised agent modes chain together to produce every artefact in a standard software delivery lifecycle, with zero human-written code.

---

## What was built

A production-grade **Payment Service** — a double-entry ledger microservice for a Venmo-like peer-to-peer payments platform — generated entirely by Bob agents.

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Migrations | Liquibase |
| Messaging | RabbitMQ (domain events) |
| Testing | JUnit 5, Testcontainers, JaCoCo |
| Build | Maven |

---

## Agent workflow

Each stage was driven by a dedicated Bob agent mode. The stages ran sequentially, with each agent consuming the previous stage's output.

```
User Idea
    │
    ▼
┌─────────────────────────┐
│  Product Owner Agent     │  → SRS document
│  (product-agent mode)    │    ISO/IEC/IEEE 29148-compliant
└─────────────┬───────────┘
              │
              ▼
┌─────────────────────────┐
│  Technical Architect     │  → SDD document
│  Agent (technical-       │    IEEE 1016 / arc42-compliant
│  architect mode)         │    + ADRs + Liquibase migrations
└─────────────┬───────────┘
              │
              ▼
┌─────────────────────────┐
│  Delivery Agent          │  → Product backlog + sprint plan
│  (delivery-agent mode)   │    INVEST stories, Fibonacci points
└─────────────┬───────────┘
              │
              ▼
┌─────────────────────────┐
│  Testing Engineer Agent  │  → Test plan, test cases, BDD features
│  (testing-agent mode)    │    RTM mapping REQ-IDs → TC-IDs
└─────────────┬───────────┘
              │
              ▼
┌─────────────────────────┐
│  Coding Agent            │  → Full Spring Boot service
│  (coding-agent mode)     │    Unit + integration tests
└─────────────┬───────────┘
              │
              ▼
         Passing tests
         & merged code
```

---

## Repository structure

```
bob2-dev-automation/
├── product-documentation/
│   └── SRS-payment-service.md          # Requirements (REQ-F-001 … REQ-NF-013)
│
├── technical-documentation/
│   ├── SDD-payment-service.md          # Architecture, API contracts, data model
│   ├── adrs/
│   │   ├── ADR-001-double-entry-ledger.md
│   │   ├── ADR-002-pessimistic-locking.md
│   │   └── ADR-003-idempotency-key-table.md
│   └── migrations/
│       └── 001-initial-schema.xml      # Reference Liquibase changelog
│
├── delivery-documentation/
│   ├── BACKLOG-payment-service.md      # 10 user stories, story points, sub-tasks
│   └── SPRINT-PLAN-payment-service.md  # 2-sprint plan (52 pts, 3 devs)
│
├── testing-documentation/
│   ├── TEST-PLAN-payment-service.md
│   ├── TEST-DESIGN-payment-service.md
│   ├── TEST-CASES-payment-service.md   # TC-IDs with BVA / EP / state-transition
│   ├── RTM-payment-service.md          # Requirements traceability matrix
│   └── features/
│       ├── transfer-execution.feature
│       ├── idempotency.feature
│       └── balance-safety.feature
│
├── payment-service/                    # Generated Spring Boot microservice
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/payments/
│       │   ├── controller/             # REST endpoints
│       │   ├── service/                # Business logic
│       │   ├── domain/                 # JPA entities
│       │   ├── repository/             # Spring Data repositories
│       │   ├── dto/                    # Request/response objects
│       │   ├── exception/              # Domain exceptions + global handler
│       │   └── config/                 # RabbitMQ configuration
│       └── test/java/com/payments/
│           ├── unit/                   # Unit tests (Mockito)
│           └── integration/            # Integration tests (Testcontainers)
│
└── .bob/skills/                        # Custom Bob skills used in this project
    ├── srs-generator/
    ├── sdd-generator/
    ├── backlog-generator/
    ├── test-doc-generator/
    ├── coding-agent-workflow/
    ├── code-review-and-merge/
    └── human-escalation/
```

---

## Payment Service — key features

The service implements the following financial correctness guarantees:

- **Double-entry bookkeeping** — every transfer writes an immutable debit and credit journal entry; net ledger delta is always zero.
- **Idempotency** — client-supplied `Idempotency-Key` UUID header guarantees exactly-once execution across retries.
- **Pessimistic concurrency control** — `SELECT FOR UPDATE` locks accounts in ascending ID order; prevents deadlocks and negative-balance races.
- **Deadlock retry** — up to 3 attempts with exponential backoff before returning HTTP 409.
- **Immutable ledger** — committed `LedgerEntry` rows are never updated or deleted; reversals create offsetting entries.
- **Monetary precision** — `NUMERIC(19,4)` in PostgreSQL, `BigDecimal` (scale 4, `HALF_EVEN`) throughout Java; no `double`/`float`.
- **Domain events** — `PaymentCompletedEvent` published to RabbitMQ after every successful transfer.

### API endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/transfers` | Initiate a transfer (requires `Idempotency-Key` header) |
| `GET` | `/v1/transfers/{id}` | Query transfer status |
| `GET` | `/v1/accounts/{id}/balance` | Get current account balance |

### Running locally

```bash
# Start PostgreSQL and RabbitMQ
docker-compose -f payment-service/docker-compose.yml up -d

# Build and run tests
cd payment-service
mvn verify

# Start the service
mvn spring-boot:run
```

---

## Bob skills used

Custom skills in `.bob/skills/` provided Bob agents with detailed, step-by-step instructions for each phase:

| Skill | Purpose |
|---|---|
| `srs-generator` | ISO/IEC/IEEE 29148-compliant SRS with REQ-IDs and Given/When/Then acceptance criteria |
| `sdd-generator` | IEEE 1016 / arc42 SDD with C4 diagrams, API contracts, Liquibase plan, ADRs |
| `backlog-generator` | INVEST user stories with Fibonacci points, sprint assignment, and sub-tasks |
| `test-doc-generator` | Test plan, BDD Gherkin features, TC-IDs, and RTM using BVA/EP/state-transition |
| `coding-agent-workflow` | Sub-task-by-sub-task implementation with per-file inline review |
| `code-review-and-merge` | Final review gate: full test suite, coverage check, no TODOs, then merge |
| `human-escalation` | Posts structured questions to Mattermost when an agent lacks context to proceed |

---

## Global configuration

[`token-budget-plan.md`](token-budget-plan.md) describes a global Bob hook (`~/.bob/hooks/token-guard.mjs`) that enforces an 80,000-token conversation budget by:

1. Blocking prompts longer than 5,000 characters.
2. Blocking conversations that exceed 15 turns.
3. Instructing Bob to self-monitor and warn when context approaches 60k–80k tokens.
