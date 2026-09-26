# Test Case Specification: Payment Service

**Document ID:** TCS-20250115-001  
**Version:** 1.0  
**Status:** Draft  
**Standard:** ISO/IEC/IEEE 29119-3:2021  
**Test Plan Reference:** TP-20250115-001  
**Test Design Reference:** TDS-20250115-001  
**SRS Reference:** SRS-20250115-001  

---

## Conventions

- **TC-NNN:** Three-digit zero-padded identifier, globally unique within this specification.
- **Test Data:** All amounts in cents (integer minor currency units) unless stated otherwise.  
  String BigDecimal literals used wherever the code accepts a string (`"25.0000"`).
- **DB Net-Zero Assert:** After every integration test involving a committed transfer, the following SQL must return 0:  
  `SELECT SUM(CASE entry_type WHEN 'CREDIT' THEN amount WHEN 'DEBIT' THEN -amount END) FROM ledger_entries WHERE transaction_id = :txnId`
- **Framework shorthand:** `@SBWT` = `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers PostgreSQL.

---

## Section A — Transfer Execution (REQ-F-001 through REQ-F-006)

---

## TC-001: Successful transfer with sufficient balance

**REQ-ID(s):** REQ-F-001, REQ-F-006, REQ-F-015, REQ-F-017  
**Test Level:** Integration  
**Technique:** EP (EP-AMT-01, EP-ACC-01)  
**Priority:** Critical  

### Preconditions
- PostgreSQL running via Testcontainers; schema applied via Liquibase.
- Account A exists with balance 10000 cents.
- Account B exists with balance 5000 cents.
- A unique idempotency key `idk-tc001` is not present in `idempotency_keys`.

### Test Data
| Parameter | Value | Notes |
|---|---|---|
| senderAccountId | Account A UUID | Seeded in `@BeforeEach` |
| receiverAccountId | Account B UUID | Seeded in `@BeforeEach` |
| amount | "25.0000" | 2500 cents |
| currency | "USD" | |
| Idempotency-Key | "idk-tc001" | Fresh UUID |
| X-User-Id | User UUID | Injected header |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with the test data above | HTTP 201 Created |
| 2 | Assert response body contains `transactionId` (non-null UUID) | `transactionId` present |
| 3 | Assert response body `status` = "COMPLETED" | `status` = "COMPLETED" |
| 4 | Assert response body `amount` = "25.0000" | Amount matches |
| 5 | Query `accounts` table for Account A balance | balance = 7500 (10000 − 2500) |
| 6 | Query `accounts` table for Account B balance | balance = 7500 (5000 + 2500) |
| 7 | Query `ledger_entries` for `transaction_id` | Exactly 2 rows returned |
| 8 | Assert one row has `entry_type` = 'DEBIT', `account_id` = A | DEBIT entry present |
| 9 | Assert one row has `entry_type` = 'CREDIT', `account_id` = B | CREDIT entry present |
| 10 | Execute DB net-zero assertion SQL | Result = 0.0000 |

### Expected Outcome
HTTP 201 is returned with a non-null `transactionId` and `status: COMPLETED`; Account A balance decrements by 2500, Account B increments by 2500; exactly two ledger entries exist and their net sum equals zero.

### Postconditions
- `idempotency_keys` table contains a COMPLETED record for `idk-tc001`.
- No other accounts are affected.

---

## TC-002: Transfer rejected — insufficient funds

**REQ-ID(s):** REQ-F-002  
**Test Level:** Integration  
**Technique:** EP (EP-AMT-05), BVA (BVA-AMT-06)  
**Priority:** Critical  

### Preconditions
- Account A exists with balance 1000 cents.
- Account B exists.

### Test Data
| Parameter | Value | Notes |
|---|---|---|
| senderAccountId | Account A UUID | |
| receiverAccountId | Account B UUID | |
| amount | "50.0000" | 5000 cents — exceeds balance |
| Idempotency-Key | fresh UUID | |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers | HTTP 422 Unprocessable Entity |
| 2 | Assert response `errorCode` = "INSUFFICIENT_FUNDS" | Error code matches |
| 3 | Assert response `message` contains sender balance and requested amount | Informative message |
| 4 | Assert response `traceId` is non-null | Trace ID present |
| 5 | Query `ledger_entries` for this request's transaction context | Zero rows |
| 6 | Query Account A balance | Unchanged at 1000 |
| 7 | Query Account B balance | Unchanged |

### Expected Outcome
HTTP 422 with `errorCode: INSUFFICIENT_FUNDS`; zero ledger entries created; both account balances unchanged.

### Postconditions
- No transaction record created (or record exists in FAILED status with no ledger entries).

---

## TC-003: Transfer rejected — amount is zero

**REQ-ID(s):** REQ-F-003  
**Test Level:** Unit + Integration  
**Technique:** BVA (BVA-AMT-02), EP (EP-AMT-03)  
**Priority:** Critical  

### Preconditions
- Account A exists with balance 10000 cents.
- Account B exists.

### Test Data
| Parameter | Value | Notes |
|---|---|---|
| amount | "0.0000" | Exactly zero |
| Idempotency-Key | fresh UUID | |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "0.0000" | HTTP 400 Bad Request |
| 2 | Assert `errorCode` = "INVALID_AMOUNT" | Error code matches |
| 3 | Query `ledger_entries` | Zero rows |
| 4 | Query Account A balance | Unchanged at 10000 |

### Expected Outcome
HTTP 400 with `errorCode: INVALID_AMOUNT`; no balance changes; no ledger entries.

### Postconditions
- No records created.

---

## TC-004: Transfer rejected — negative amount

**REQ-ID(s):** REQ-F-003  
**Test Level:** Unit + Integration  
**Technique:** BVA (BVA-AMT-01), EP (EP-AMT-04)  
**Priority:** Critical  

### Preconditions
- Account A exists with positive balance.

### Test Data
| Parameter | Value | Notes |
|---|---|---|
| amount | "-0.0100" | Negative value |
| Idempotency-Key | fresh UUID | |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "-0.0100" | HTTP 400 Bad Request |
| 2 | Assert `errorCode` = "INVALID_AMOUNT" | |
| 3 | Query `ledger_entries` | Zero rows |

### Expected Outcome
HTTP 400 with `errorCode: INVALID_AMOUNT`; no mutations.

### Postconditions
- No records created.

---

## TC-005: Transfer rejected — self-transfer

**REQ-ID(s):** REQ-F-004  
**Test Level:** Unit + Integration  
**Technique:** EP (EP-ACC-04), DT (DT-TRF-04)  
**Priority:** High  

### Preconditions
- Account A exists with balance 10000 cents.

### Test Data
| Parameter | Value | Notes |
|---|---|---|
| senderAccountId | Account A UUID | |
| receiverAccountId | Account A UUID | Same as sender |
| amount | "10.0000" | |
| Idempotency-Key | fresh UUID | |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with sender = receiver | HTTP 422 |
| 2 | Assert `errorCode` = "SELF_TRANSFER_NOT_ALLOWED" | |
| 3 | Query `ledger_entries` | Zero rows |
| 4 | Query Account A balance | Unchanged at 10000 |

### Expected Outcome
HTTP 422 with `errorCode: SELF_TRANSFER_NOT_ALLOWED`; no mutations.

### Postconditions
- No records created.

---

## TC-006: Transfer rejected — missing Idempotency-Key header

**REQ-ID(s):** REQ-F-007  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-01), DT (DT-TRF-01)  
**Priority:** High  

### Preconditions
- Valid sender and receiver accounts exist.

### Test Data
| Parameter | Value |
|---|---|
| Idempotency-Key header | ABSENT |
| amount | "10.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers without `Idempotency-Key` header | HTTP 400 |
| 2 | Assert `errorCode` = "MISSING_IDEMPOTENCY_KEY" | |

### Expected Outcome
HTTP 400 with `errorCode: MISSING_IDEMPOTENCY_KEY`.

### Postconditions
- No records created.

---

## TC-007: Transfer with exact balance (boundary — succeeds)

**REQ-ID(s):** REQ-F-001, REQ-F-002  
**Test Level:** Integration  
**Technique:** BVA (BVA-AMT-05)  
**Priority:** Critical  

### Preconditions
- Account A balance = 10000 cents exactly.
- Account B exists.

### Test Data
| Parameter | Value |
|---|---|
| amount | "100.0000" | 10000 cents = exact balance |
| Idempotency-Key | fresh UUID |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "100.0000" | HTTP 201 Created |
| 2 | Query Account A balance | 0 (zero) |
| 3 | Query Account B balance | Increased by 10000 |
| 4 | Execute DB net-zero assertion | = 0.0000 |
| 5 | Assert `accounts.balance` CHECK constraint still holds | No constraint violation; balance = 0.0000 ≥ 0 |

### Expected Outcome
Transfer succeeds; Account A balance reaches exactly zero (not negative); net-zero ledger invariant holds.

### Postconditions
- Account A balance = 0. No negative balance condition.

---

## TC-008: Transfer at balance + 1 cent (boundary — fails)

**REQ-ID(s):** REQ-F-002, REQ-F-013  
**Test Level:** Integration  
**Technique:** BVA (BVA-AMT-06)  
**Priority:** Critical  

### Preconditions
- Account A balance = 10000 cents.
- Account B exists.

### Test Data
| Parameter | Value |
|---|---|
| amount | "100.0100" | 10001 cents — 1 cent above balance |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "100.0100" | HTTP 422 |
| 2 | Assert `errorCode` = "INSUFFICIENT_FUNDS" | |
| 3 | Query Account A balance | Still 10000; no mutation |

### Expected Outcome
HTTP 422 `INSUFFICIENT_FUNDS`; no balance changes; DB CHECK constraint never challenged.

---

## TC-009: Transfer amount of 1 cent (boundary — minimum valid)

**REQ-ID(s):** REQ-F-001, REQ-F-003  
**Test Level:** Integration  
**Technique:** BVA (BVA-AMT-03)  
**Priority:** High  

### Preconditions
- Account A balance = 10000 cents.
- Account B exists.

### Test Data
| Parameter | Value |
|---|---|
| amount | "0.0100" | 1 cent |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "0.0100" | HTTP 201 Created |
| 2 | Query Account A balance | 9999 cents |
| 3 | Execute DB net-zero assertion | = 0.0000 |

### Expected Outcome
Transfer of 1 cent succeeds; balances updated correctly.

---

## TC-010: Sender account does not exist

**REQ-ID(s):** REQ-F-001  
**Test Level:** Integration  
**Technique:** EP (EP-ACC-02)  
**Priority:** High  

### Preconditions
- Receiver account exists.
- Sender UUID is a random value not in `accounts` table.

### Test Data
| Parameter | Value |
|---|---|
| senderAccountId | Random UUID (not in DB) |
| amount | "10.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers | HTTP 404 Not Found |
| 2 | Assert `errorCode` = "ACCOUNT_NOT_FOUND" | |

### Expected Outcome
HTTP 404 with `errorCode: ACCOUNT_NOT_FOUND`.

---

## Section B — Double-Entry Ledger (REQ-F-015 through REQ-F-017)

---

## TC-011: Ledger entries contain all required fields

**REQ-ID(s):** REQ-F-015  
**Test Level:** Integration  
**Technique:** DT (DT-LED-01)  
**Priority:** Critical  

### Preconditions
- Successful transfer from Account A to Account B exists (see TC-001 setup).

### Test Data
- Same as TC-001.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Execute TC-001 transfer | HTTP 201 Created |
| 2 | Query `ledger_entries` for the transaction | 2 rows returned |
| 3 | Assert each row: `account_id` non-null | Present |
| 4 | Assert each row: `entry_type` ∈ {DEBIT, CREDIT} | Valid enum value |
| 5 | Assert each row: `amount` = "25.0000" | Correct amount |
| 6 | Assert each row: `currency` = "USD" | Correct currency |
| 7 | Assert each row: `request_timestamp` non-null | Timestamp set |
| 8 | Assert each row: `transaction_id` = returned txnId | FK correct |
| 9 | Assert each row: `initiating_user_id` = X-User-Id value | User ID captured |

### Expected Outcome
Both ledger entries contain all nine required fields with correct values.

---

## TC-012: Ledger entries are immutable — UPDATE rejected

**REQ-ID(s):** REQ-F-016  
**Test Level:** Integration  
**Technique:** DT (DT-LED-03)  
**Priority:** Critical  

### Preconditions
- Successful transfer committed; at least one `ledger_entries` row exists.

### Test Data
| Parameter | Value |
|---|---|
| SQL | `UPDATE ledger_entries SET amount = 99999 WHERE id = :entryId` |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Obtain `ledger_entry_id` from TC-001 transfer | ID captured |
| 2 | Attempt UPDATE via `JdbcTemplate` using app DB user | Exception thrown (`DataAccessException` or permission denied) |
| 3 | Re-query `ledger_entries` row | Amount unchanged |

### Expected Outcome
UPDATE is rejected at the DB layer (permission denied on `ledger_entries` for app user) OR `UnsupportedOperationException` thrown by `@Immutable` JPA entity. Amount in DB unchanged.

---

## TC-013: Net-zero invariant holds after transfer

**REQ-ID(s):** REQ-F-017  
**Test Level:** Integration  
**Technique:** DT (DT-LED-01)  
**Priority:** Critical  

### Preconditions
- Clean account state; three separate transfers will be executed.

### Test Data
| Transfer | Amount |
|---|---|
| Transfer 1 | "25.0000" |
| Transfer 2 | "0.0100" |
| Transfer 3 | "999.9999" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Execute Transfer 1 | HTTP 201 |
| 2 | Execute DB net-zero assertion for Transfer 1 txnId | = 0.0000 |
| 3 | Execute Transfer 2 | HTTP 201 |
| 4 | Execute DB net-zero assertion for Transfer 2 txnId | = 0.0000 |
| 5 | Execute Transfer 3 | HTTP 201 |
| 6 | Execute DB net-zero assertion for Transfer 3 txnId | = 0.0000 |

### Expected Outcome
For every transfer, the net sum of all ledger entries equals exactly 0.0000.

---

## TC-014: Rollback on validation failure — zero ledger entries

**REQ-ID(s):** REQ-F-015, REQ-F-016  
**Test Level:** Integration  
**Technique:** DT (DT-LED-02)  
**Priority:** Critical  

### Preconditions
- Account A balance = 100 cents.
- Attempted transfer amount = 10000 cents (insufficient funds).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with insufficient amount | HTTP 422 |
| 2 | Count `ledger_entries` rows for any transaction from Account A in this test | 0 |

### Expected Outcome
Zero ledger entries exist for the failed transfer attempt; atomicity preserved.

---

## TC-015: LedgerIntegrityException when DEBIT ≠ CREDIT (internal corruption guard)

**REQ-ID(s):** REQ-F-017  
**Test Level:** Unit  
**Technique:** DT (DT-LED-05)  
**Priority:** Critical  

### Preconditions
- `LedgerService` under test with mocked repository.
- Inject a corrupt scenario where debit amount ≠ credit amount.

### Test Data
| Parameter | Value |
|---|---|
| debitAmount | "25.0000" |
| creditAmount | "24.9999" | Deliberately mismatched |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Call `ledgerService.recordTransfer()` with mismatched amounts | `LedgerIntegrityException` thrown |
| 2 | Assert exception message contains "LEDGER_INTEGRITY_ERROR" | Message descriptive |
| 3 | Assert zero repository save calls completed | No rows written |

### Expected Outcome
`LedgerIntegrityException` is thrown before any rows are committed; service acts as a correctness guard.

---

## Section C — BigDecimal Arithmetic Precision (REQ-F-005, CON-003)

---

## TC-016: 0.1 + 0.2 = 0.3 exactly (no floating-point trap)

**REQ-ID(s):** REQ-F-005  
**Test Level:** Unit  
**Technique:** BVA (BVA-BD-01)  
**Priority:** Critical  

### Preconditions
- `BigDecimalMonetaryHelper` (or equivalent arithmetic utility) under test.

### Test Data
| Parameter | Value |
|---|---|
| credit amount | "0.1000" |
| debit amount | "0.2000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | credit(BigDecimal("0.0000"), new BigDecimal("0.1000")) | "0.1000" |
| 2 | credit("0.1000", new BigDecimal("0.2000")) | "0.3000" |
| 3 | debit("0.3000", new BigDecimal("0.3000")) | "0.0000" |
| 4 | assertThat(result.compareTo(ZERO)).isEqualTo(0) | Passes |
| 5 | assertThat(result.toPlainString()).isEqualTo("0.0000") | Exact string match |

### Expected Outcome
`0.1 + 0.2 - 0.3 == 0.0000` exactly using `BigDecimal` arithmetic with scale=4 and `HALF_EVEN` rounding.

---

## TC-017: Penny split across 3 recipients — no fractional cent lost

**REQ-ID(s):** REQ-F-005  
**Test Level:** Unit  
**Technique:** BVA (BVA-BD-02)  
**Priority:** Critical  

### Preconditions
- `BigDecimalMonetaryHelper` under test.

### Test Data
| Parameter | Value |
|---|---|
| total amount | "10.0000" (1000 cents) |
| split into | 3 equal parts |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Divide "10.0000" by 3 with scale=4, HALF_EVEN | "3.3333" (rounded) |
| 2 | Compute remainder: 10.0000 − 3 × 3.3333 | "0.0001" (1 penny remainder) |
| 3 | Assert part1 + part2 + part3 + remainder = "10.0000" | Sum = original |
| 4 | Assert no part has scale > 4 | Scale assertion passes |

### Expected Outcome
Three-way split using `HALF_EVEN` produces parts that sum back to the original total when remainder is handled explicitly. No cents lost or created.

---

## TC-018: Minimum representable unit (0.0001) transfers correctly

**REQ-ID(s):** REQ-F-005  
**Test Level:** Integration  
**Technique:** BVA (BVA-BD-03)  
**Priority:** High  

### Preconditions
- Account A balance = 10000.0000.
- Account B exists.

### Test Data
| Parameter | Value |
|---|---|
| amount | "0.0001" | Minimum 4dp unit |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "0.0001" | HTTP 201 Created |
| 2 | Query Account A balance | 9999.9999 |
| 3 | Query `ledger_entries` | Both entries have amount = "0.0001" |
| 4 | DB net-zero assertion | = 0.0000 |

### Expected Outcome
Minimum unit transfer succeeds; stored and retrieved as "0.0001" without precision loss.

---

## TC-019: Scale violation — amount with more than 4 decimal places rejected

**REQ-ID(s):** REQ-F-005  
**Test Level:** Integration  
**Technique:** BVA (BVA-BD-04)  
**Priority:** High  

### Preconditions
- Valid sender and receiver accounts.

### Test Data
| Parameter | Value |
|---|---|
| amount | "0.00001" | 5 decimal places |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with amount = "0.00001" | HTTP 400 |
| 2 | Assert `errorCode` = "INVALID_AMOUNT" | |

### Expected Outcome
HTTP 400 rejects amounts with scale > 4.

---

## TC-020: BigDecimal(double) constructor is never used

**REQ-ID(s):** REQ-F-005, CON-003  
**Test Level:** Unit (static analysis / reflection test)  
**Technique:** BVA (BVA-BD-06)  
**Priority:** Critical  

### Preconditions
- Test exercises the arithmetic utility directly.

### Test Data
| Parameter | Value |
|---|---|
| input | `new BigDecimal(0.1)` — forbidden constructor |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Construct `new BigDecimal(0.1)` and call `.toPlainString()` | Returns "0.1000000000000000055511151231257827021181583404541015625" |
| 2 | Confirm production code paths use `new BigDecimal("0.1000")` or `BigDecimal.valueOf()` only | Static code scan / ArchUnit rule |
| 3 | Assert that `normalize(new BigDecimal("0.1"))` gives exactly "0.1000" | Scale normalisation correct |

### Expected Outcome
Test documents the forbidden constructor trap; `normalize()` helper always uses string form internally. ArchUnit rule enforces no `new BigDecimal(double)` in `src/main/`.

---

## Section D — Idempotency (REQ-F-007 through REQ-F-011)

---

## TC-021: Idempotent replay of completed transfer returns original response

**REQ-ID(s):** REQ-F-008  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-03), DT (DT-IDK-04)  
**Priority:** Critical  

### Preconditions
- A transfer with `Idempotency-Key: idk-tc021` completed successfully.
- Account A debited 2500; Account B credited 2500.
- `transactionId-original` recorded from first response.

### Test Data
| Parameter | Value |
|---|---|
| Idempotency-Key | "idk-tc021" | Same key as original |
| senderAccountId | Same as original |
| receiverAccountId | Same as original |
| amount | "25.0000" | Same as original |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with same key and payload | HTTP 200 OK (not 201) |
| 2 | Assert `transactionId` in response = `transactionId-original` | Same ID returned |
| 3 | Assert `status` = "COMPLETED" | |
| 4 | Count `ledger_entries` for Account A | Still 2 (no new entries) |
| 5 | Query Account A balance | Unchanged from first transfer |
| 6 | Query Account B balance | Unchanged from first transfer |

### Expected Outcome
HTTP 200 with original `transactionId`; exactly zero new ledger entries; both balances unchanged.

---

## TC-022: In-progress transfer returns HTTP 409

**REQ-ID(s):** REQ-F-009  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-04), DT (DT-IDK-03)  
**Priority:** Critical  

### Preconditions
- An `idempotency_keys` row exists with `key = "idk-tc022"` and `status = 'PENDING'` (simulated by direct DB insert before the request).

### Test Data
| Parameter | Value |
|---|---|
| Idempotency-Key | "idk-tc022" |
| idempotency_keys row | key="idk-tc022", status='PENDING', created_at=NOW(), expires_at=NOW()+24h |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Insert PENDING idempotency_keys record via `JdbcTemplate` | Record inserted |
| 2 | POST /v1/transfers with key "idk-tc022" | HTTP 409 Conflict |
| 3 | Assert `errorCode` = "TRANSFER_IN_PROGRESS" | |
| 4 | Count `ledger_entries` for any associated transaction | 0 |

### Expected Outcome
HTTP 409 with `errorCode: TRANSFER_IN_PROGRESS`; no transfer executed.

---

## TC-023: Idempotency key reused with different payload — HTTP 422

**REQ-ID(s):** REQ-F-010  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-05), DT (DT-IDK-05)  
**Priority:** Critical  

### Preconditions
- Transfer `idk-tc023` completed for amount "100.0000".

### Test Data
| Parameter | Value |
|---|---|
| Idempotency-Key | "idk-tc023" | Same key |
| amount | "200.0000" | DIFFERENT from original 100.0000 |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with same key but amount = "200.0000" | HTTP 422 |
| 2 | Assert `errorCode` = "IDEMPOTENCY_KEY_CONFLICT" | |
| 3 | Count `ledger_entries` for Account A | Only 2 (from original); no new entries |

### Expected Outcome
HTTP 422 `IDEMPOTENCY_KEY_CONFLICT`; no second transfer executed.

---

## TC-024: Retry of failed transfer with same key re-executes

**REQ-ID(s):** REQ-F-008, REQ-F-009  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-06), DT (DT-IDK-06)  
**Priority:** High  

### Preconditions
- An `idempotency_keys` row exists with `status = 'FAILED'` and matching payload hash.
- Sender has sufficient balance for the retry.

### Test Data
| Parameter | Value |
|---|---|
| Idempotency-Key | "idk-tc024" |
| idempotency_keys row | status='FAILED', payload_hash=sha256(sender|receiver|amount) |
| amount | "10.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers with key "idk-tc024" | HTTP 201 Created |
| 2 | Assert new `transactionId` returned | Non-null |
| 3 | Assert `ledger_entries` count for new txnId = 2 | Two entries written |
| 4 | DB net-zero assertion | = 0.0000 |

### Expected Outcome
Failed transfer is re-executed successfully on retry; new transaction created with correct ledger entries.

---

## TC-025: Idempotency key survives service restart

**REQ-ID(s):** REQ-F-011, REQ-NF-015  
**Test Level:** Integration  
**Technique:** DT (DT-IDK-04)  
**Priority:** High  

### Preconditions
- Transfer `idk-tc025` completed successfully.
- Testcontainer PostgreSQL instance remains up (application context restart simulated by creating a new `ApplicationContext` or making the request from a fresh test client).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Execute transfer with key "idk-tc025" | HTTP 201, `transactionId-orig` |
| 2 | Simulate service restart (restart Spring context in test) | Context reloads; DB data persists |
| 3 | POST /v1/transfers again with same key and payload | HTTP 200 OK |
| 4 | Assert returned `transactionId` = `transactionId-orig` | Same ID from DB |

### Expected Outcome
Idempotency key record persists in PostgreSQL across restart; replay still returns original response.

---

## TC-026: Two concurrent same-key requests — exactly one succeeds

**REQ-ID(s):** REQ-F-007, REQ-F-009  
**Test Level:** Integration  
**Technique:** EP (EP-IDK-04), DT (DT-IDK-03)  
**Priority:** Critical  

### Preconditions
- Sender has 10000 cents balance.
- A fresh idempotency key "idk-tc026" is not in DB.

### Test Data
| Parameter | Value |
|---|---|
| Thread count | 2 |
| Idempotency-Key | "idk-tc026" — same for both threads |
| amount | "50.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Use `CountDownLatch(1)` to synchronise thread start | Both threads ready |
| 2 | Release latch; both threads fire POST /v1/transfers simultaneously | Concurrent execution |
| 3 | Collect HTTP status codes from both threads | |
| 4 | Assert exactly one HTTP 201 and one HTTP 409 | One succeeds; one gets 409 |
| 5 | Count `ledger_entries` for sender account | Exactly 2 rows (one transfer) |
| 6 | Assert sender balance decremented exactly once | Balance = 5000 (not 0 or negative) |

### Expected Outcome
Exactly one transfer executes; DB unique constraint on idempotency key prevents double execution; ledger has exactly 2 entries; balance decremented once.

---

## Section E — Concurrency and Balance Safety (REQ-F-012 through REQ-F-014)

---

## TC-027: Two concurrent debits — only one succeeds when balance allows only one

**REQ-ID(s):** REQ-F-012, REQ-F-013  
**Test Level:** Integration  
**Technique:** EP (EP-AMT-05), STT (STT-TXN-03)  
**Priority:** Critical  

### Preconditions
- Account A has balance 1000 cents.
- Account B and Account C exist as receivers.
- `@RepeatedTest(20)` applied to amplify race detection probability.

### Test Data
| Parameter | Value |
|---|---|
| Thread 1 amount | "8.0000" | 800 cents |
| Thread 2 amount | "8.0000" | 800 cents |
| Account A balance | 1000 cents |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Create `CountDownLatch(1)` and `ExecutorService` with 2 threads | |
| 2 | Both threads prepare POST /v1/transfers (A→B 800 cents, different idempotency keys) | |
| 3 | Release latch; both threads submit simultaneously | Concurrent execution |
| 4 | Await both threads to complete | |
| 5 | Collect HTTP status codes | |
| 6 | Assert exactly one HTTP 201 | One thread succeeded |
| 7 | Assert exactly one HTTP 422 with `errorCode: INSUFFICIENT_FUNDS` | Other thread failed |
| 8 | Query Account A balance | Exactly 200 cents (1000 − 800) |
| 9 | Count `ledger_entries` for Account A | Exactly 2 rows (one transfer) |
| 10 | DB net-zero assertion for the successful transaction | = 0.0000 |

### Expected Outcome
Exactly one debit succeeds; Account A balance = 200 cents (no money created or destroyed); exactly 2 ledger entries exist.

---

## TC-028: Database-level CHECK constraint prevents negative balance

**REQ-ID(s):** REQ-F-013  
**Test Level:** Integration  
**Technique:** BVA, DT  
**Priority:** Critical  

### Preconditions
- Account A balance = 0 cents.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Execute: `UPDATE accounts SET balance = -0.0001 WHERE id = :accountId` via JdbcTemplate | `DataIntegrityViolationException` thrown |
| 2 | Re-query Account A balance | Still 0.0000 |

### Expected Outcome
PostgreSQL `CHECK (balance >= 0)` constraint fires; update is rejected; balance stays at 0.

---

## TC-029: Deadlock retry succeeds on second attempt

**REQ-ID(s):** REQ-F-014  
**Test Level:** Unit (with Mockito spy on retry mechanism)  
**Technique:** STT (STT-DLK-04)  
**Priority:** Critical  

### Preconditions
- `TransferService` configured with `@Retryable` (maxAttempts=3).
- Mock `accountRepository.findByIdForUpdate()` to throw `CannotAcquireLockException` on attempt 1, then succeed on attempt 2.

### Test Data
| Parameter | Value |
|---|---|
| Mock behaviour | Attempt 1: throw `CannotAcquireLockException`; Attempt 2: return locked accounts |
| amount | "10.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Call `transferService.initiateTransfer()` | Attempt 1 throws |
| 2 | Spring Retry triggers retry after ~50ms backoff | Attempt 2 called |
| 3 | Attempt 2 succeeds | `TransferResult` returned with COMPLETED |
| 4 | Verify `accountRepository.findByIdForUpdate()` invoked exactly 2 times | Retry counted correctly |
| 5 | Verify ledger entries created exactly once | No double-write |

### Expected Outcome
Transfer completes on second attempt; Spring Retry fires exactly once; ledger written exactly once.

---

## TC-030: Deadlock exhaustion after 3 retries returns HTTP 503

**REQ-ID(s):** REQ-F-014  
**Test Level:** Unit + Integration  
**Technique:** STT (STT-DLK-03)  
**Priority:** High  

### Preconditions
- Mock `findByIdForUpdate()` to always throw `CannotAcquireLockException` (3 times).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Call `transferService.initiateTransfer()` | Attempt 1 throws |
| 2 | Retry 1 | Throws again |
| 3 | Retry 2 | Throws again |
| 4 | `@Recover` invoked | `DeadlockExhaustedException` thrown |
| 5 | Integration: POST /v1/transfers | HTTP 503 Service Unavailable |
| 6 | Assert `errorCode` = "TRANSFER_DEADLOCK_EXHAUSTED" | |
| 7 | Count `ledger_entries` | 0 |

### Expected Outcome
HTTP 503 `TRANSFER_DEADLOCK_EXHAUSTED` after 3 failed lock attempts; zero ledger entries.

---

## TC-031: Lock acquisition order — lower UUID acquired first (deadlock prevention)

**REQ-ID(s):** REQ-F-012  
**Test Level:** Unit  
**Technique:** DT  
**Priority:** High  

### Preconditions
- Account A UUID: "00000000-0000-0000-0000-000000000001"
- Account B UUID: "ffffffff-ffff-ffff-ffff-ffffffffffff"
- Transfer from B → A (reverse of natural UUID order)

### Test Data
| Parameter | Value |
|---|---|
| senderAccountId | UUID "ffff…" (higher) |
| receiverAccountId | UUID "0000…" (lower) |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Capture IDs passed to `accountRepository.findByIdForUpdate()` | |
| 2 | Assert the list is sorted ascending by UUID | First ID = "0000…" |
| 3 | Assert lock SQL contains `ORDER BY id` | ORDER BY present in query |

### Expected Outcome
Lock acquisition always uses ascending UUID order regardless of sender/receiver direction, preventing deadlocks.

---

## TC-032: Concurrent transfers amplified — @RepeatedTest race detection

**REQ-ID(s):** REQ-F-012, REQ-F-013  
**Test Level:** Integration  
**Technique:** EP  
**Priority:** Critical  

### Preconditions
- Account A balance = 10000 cents.
- 10 concurrent threads each attempt to transfer 1500 cents from Account A to different receivers.
- 10 transfers × 1500 = 15000 > 10000, so at most 6 can succeed.

### Test Data
| Parameter | Value |
|---|---|
| Thread count | 10 |
| Amount per transfer | "15.0000" | 1500 cents |
| Starting balance | 10000 cents |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | `@RepeatedTest(10)` wrapping the concurrent scenario | Run 10 times |
| 2 | Spin 10 threads; all fire simultaneously via `CountDownLatch` | |
| 3 | Collect HTTP status codes | |
| 4 | Count HTTP 201 responses | ≤ 6 successes |
| 5 | Count HTTP 422 responses | ≥ 4 failures |
| 6 | Query Account A balance | ≥ 0 (never negative) |
| 7 | Count `ledger_entries` for Account A | = 2 × (success count) |
| 8 | DB net-zero assertion for every successful txnId | Each = 0.0000 |

### Expected Outcome
Account A balance never goes negative; total debited = 1500 × (success count); net-zero holds for every committed transaction.

---

## TC-033: Mid-flight failure (after ledger write, before event publish) — retry safe

**REQ-ID(s):** REQ-F-014, REQ-F-008  
**Test Level:** Unit  
**Technique:** STT (STT-TXN-08)  
**Priority:** Critical  

### Preconditions
- `TransferService` with mocked `eventPublisher`.
- Event publisher throws exception (simulates broker unavailability).
- `@TransactionalEventListener(afterCommit = true)` means DB commit happens before event publication.

### Test Data
| Parameter | Value |
|---|---|
| Event publisher behaviour | Always throw `RuntimeException` on first publish attempt |
| Transfer amount | "50.0000" |
| Idempotency-Key | "idk-tc033" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers — DB commit succeeds; event publish throws | HTTP 201 still returned (event is afterCommit) |
| 2 | Assert transfer status in DB = COMPLETED | DB committed |
| 3 | Count `ledger_entries` for transaction | 2 entries committed |
| 4 | Retry POST /v1/transfers with same key and payload | HTTP 200 OK (idempotent replay) |
| 5 | Count `ledger_entries` after retry | Still 2 (no double-write) |
| 6 | Assert balances | Same as after step 1; no double-debit |

### Expected Outcome
Event publish failure does NOT roll back the committed DB transaction; idempotent retry returns cached response without re-executing ledger writes.

---

## Section F — Balance Inquiry (REQ-F-018, REQ-F-019)

---

## TC-034: Balance inquiry returns current balance

**REQ-ID(s):** REQ-F-018  
**Test Level:** Integration  
**Technique:** EP (EP-BAL-01)  
**Priority:** High  

### Preconditions
- Account A exists with balance 7500 cents.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/balance | HTTP 200 OK |
| 2 | Assert `balance` = "75.0000" | Correct balance |
| 3 | Assert `currency` = "USD" | |
| 4 | Assert `accountId` = Account A UUID | |
| 5 | Assert `asOf` = null | No timestamp |

### Expected Outcome
HTTP 200 with correct balance, currency, and accountId.

---

## TC-035: Balance inquiry for non-existent account returns 404

**REQ-ID(s):** REQ-F-018  
**Test Level:** Integration  
**Technique:** EP (EP-BAL-04)  
**Priority:** High  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{randomUUID}/balance | HTTP 404 |
| 2 | Assert `errorCode` = "ACCOUNT_NOT_FOUND" | |

### Expected Outcome
HTTP 404 with `errorCode: ACCOUNT_NOT_FOUND`.

---

## TC-036: Point-in-time balance inquiry (asOf parameter)

**REQ-ID(s):** REQ-F-019  
**Test Level:** Integration  
**Technique:** EP (EP-BAL-02)  
**Priority:** Medium  

### Preconditions
- Account A had balance 5000 cents at `T0 = 2024-01-01T00:00:00Z`.
- Additional transfer executed after T0 changed balance to 3000 cents.

### Test Data
| Parameter | Value |
|---|---|
| asOf | "2024-01-01T00:00:00Z" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/balance?asOf=2024-01-01T00:00:00Z | HTTP 200 |
| 2 | Assert `balance` = "50.0000" (5000 cents as of T0) | Historical balance correct |
| 3 | Assert `asOf` = "2024-01-01T00:00:00Z" in response | |

### Expected Outcome
HTTP 200 returns historical balance computed from ledger entries up to `asOf` timestamp.

---

## Section G — Transaction History (REQ-F-020 through REQ-F-022)

---

## TC-037: Paginated transaction history — first page

**REQ-ID(s):** REQ-F-020  
**Test Level:** Integration  
**Technique:** BVA (BVA-PAGE-02), EP (EP-HIST-01)  
**Priority:** High  

### Preconditions
- Account A has exactly 50 ledger entries from prior transfers.

### Test Data
| Parameter | Value |
|---|---|
| page | 1 |
| pageSize | 20 |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions?page=1&pageSize=20 | HTTP 200 |
| 2 | Assert `entries` length = 20 | 20 entries |
| 3 | Assert entries sorted by `requestTimestamp` descending | Newest first |
| 4 | Assert `totalCount` = 50 | |
| 5 | Assert `totalPages` = 3 | |
| 6 | Assert `page` = 1 | |
| 7 | Assert `pageSize` = 20 | |

### Expected Outcome
HTTP 200 with 20 entries sorted descending; correct pagination metadata.

---

## TC-038: Paginated transaction history — last page (partial)

**REQ-ID(s):** REQ-F-020  
**Test Level:** Integration  
**Technique:** BVA (BVA-PAGE-03)  
**Priority:** High  

### Preconditions
- Account A has exactly 50 entries.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions?page=3&pageSize=20 | HTTP 200 |
| 2 | Assert `entries` length = 10 (50 − 40 = 10 remaining) | 10 entries |
| 3 | Assert `totalPages` = 3 | |

### Expected Outcome
HTTP 200 with 10 entries on the last page; partial page returned correctly.

---

## TC-039: Empty transaction history for new account

**REQ-ID(s):** REQ-F-020  
**Test Level:** Integration  
**Technique:** EP (EP-HIST-02)  
**Priority:** High  

### Preconditions
- Account exists but has no ledger entries.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions | HTTP 200 |
| 2 | Assert `entries` = [] (empty array) | |
| 3 | Assert `totalCount` = 0 | |

### Expected Outcome
HTTP 200 with empty entries list and totalCount = 0.

---

## TC-040: Transaction history filtered by date range

**REQ-ID(s):** REQ-F-021  
**Test Level:** Integration  
**Technique:** EP (EP-HIST-04)  
**Priority:** Medium  

### Preconditions
- Account A has transfers spanning January and February 2024.

### Test Data
| Parameter | Value |
|---|---|
| from | "2024-01-01" |
| to | "2024-01-31" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions?from=2024-01-01&to=2024-01-31 | HTTP 200 |
| 2 | Assert all returned entries have timestamps within January 2024 | Date filter correct |
| 3 | Assert no February entries present | Filter excludes out-of-range |

### Expected Outcome
Only January 2024 entries returned; February entries excluded.

---

## TC-041: Transaction history filtered by type DEBIT

**REQ-ID(s):** REQ-F-022  
**Test Level:** Integration  
**Technique:** EP (EP-HIST-06)  
**Priority:** Medium  

### Preconditions
- Account A has both DEBIT and CREDIT entries.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions?type=DEBIT | HTTP 200 |
| 2 | Assert all entries have `entryType` = "DEBIT" | No CREDIT entries in result |

### Expected Outcome
Only DEBIT entries returned.

---

## TC-042: PageSize exceeds maximum (101) — rejected

**REQ-ID(s):** REQ-F-020  
**Test Level:** Integration  
**Technique:** BVA (BVA-PAGE-08)  
**Priority:** Medium  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/accounts/{accountId}/transactions?pageSize=101 | HTTP 400 |

### Expected Outcome
HTTP 400 for pageSize exceeding maximum (100).

---

## Section H — Transaction Status (REQ-F-023, REQ-F-024)

---

## TC-043: Query completed transfer by ID

**REQ-ID(s):** REQ-F-023, REQ-F-024  
**Test Level:** Integration  
**Technique:** EP (EP-TXN-01)  
**Priority:** High  

### Preconditions
- Transfer `txn-001` completed; `transactionId` captured from POST response.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/transfers/{transactionId} | HTTP 200 |
| 2 | Assert `status` = "COMPLETED" | |
| 3 | Assert `amount` = original transfer amount | |
| 4 | Assert `senderAccountId` and `receiverAccountId` present | |
| 5 | Assert `createdAt` non-null | |

### Expected Outcome
HTTP 200 with complete transfer details; status = COMPLETED.

---

## TC-044: Query non-existent transfer returns 404

**REQ-ID(s):** REQ-F-023  
**Test Level:** Integration  
**Technique:** EP (EP-TXN-04)  
**Priority:** High  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/transfers/{randomUUID} | HTTP 404 |
| 2 | Assert `errorCode` = "TRANSACTION_NOT_FOUND" | |

### Expected Outcome
HTTP 404 with `errorCode: TRANSACTION_NOT_FOUND`.

---

## TC-045: Query FAILED transfer by ID

**REQ-ID(s):** REQ-F-023, REQ-F-024  
**Test Level:** Integration  
**Technique:** EP (EP-TXN-02), STT (STT-TXN-03)  
**Priority:** Medium  

### Preconditions
- Transfer exists in FAILED status (e.g., deadlock exhaustion scenario).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/transfers/{failedTransactionId} | HTTP 200 |
| 2 | Assert `status` = "FAILED" | |

### Expected Outcome
HTTP 200; `status: FAILED` returned correctly.

---

## Section I — Transfer State Machine (REQ-F-024 through REQ-F-027)

---

## TC-046: PENDING → COMPLETED state transition

**REQ-ID(s):** REQ-F-024  
**Test Level:** Integration  
**Technique:** STT (STT-TXN-02)  
**Priority:** Critical  

### Preconditions
- Transaction inserted in PENDING status; full transfer flow executed.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Observe `transactions.status` before commit | PENDING |
| 2 | Transaction commits | status = COMPLETED in DB |
| 3 | GET /v1/transfers/{transactionId} | `status: COMPLETED` |

### Expected Outcome
Status transitions from PENDING to COMPLETED upon successful commit.

---

## TC-047: PENDING → FAILED state transition (insufficient funds)

**REQ-ID(s):** REQ-F-024  
**Test Level:** Integration  
**Technique:** STT (STT-TXN-03)  
**Priority:** High  

### Preconditions
- Transfer attempted with amount exceeding balance.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers (insufficient funds) | HTTP 422 |
| 2 | Query `transactions` table | Row either absent or `status = FAILED` |

### Expected Outcome
No PENDING transaction persists; either no row or FAILED status with no ledger entries.

---

## TC-048: COMPLETED → REVERSED state transition (successful reversal)

**REQ-ID(s):** REQ-F-024, REQ-F-025, REQ-F-027  
**Test Level:** Integration, Acceptance  
**Technique:** STT (STT-TXN-05), DT (DT-REV-06)  
**Priority:** Critical  

### Preconditions
- Transfer `txn-100` is COMPLETED; original sender=A (10000 cents received back), original receiver=B has sufficient balance.

### Test Data
| Parameter | Value |
|---|---|
| transactionId | txn-100 UUID |
| Original amount | "50.0000" |

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers/txn-100/reverse | HTTP 201 Created |
| 2 | Assert response `reversalTransactionId` non-null | New txnId |
| 3 | Assert response `originalTransactionId` = txn-100 | Linked correctly |
| 4 | Query Account A balance | Increased by 5000 (restored) |
| 5 | Query Account B balance | Decreased by 5000 |
| 6 | Count new `ledger_entries` for reversal txnId | 2 (offsetting: CREDIT for A, DEBIT for B) |
| 7 | Assert reversal entries have `reversal_of_entry_id` pointing to originals | REQ-F-027 |
| 8 | Query `transactions.status` for txn-100 | REVERSED |
| 9 | DB net-zero assertion for reversal txnId | = 0.0000 |

### Expected Outcome
Original transaction → REVERSED; 2 offsetting ledger entries created; balances restored; net-zero holds on reversal transaction.

---

## TC-049: Reversal rejected — transaction already REVERSED

**REQ-ID(s):** REQ-F-026  
**Test Level:** Integration  
**Technique:** STT (STT-TXN-07), DT (DT-REV-04), EP (EP-REV-02)  
**Priority:** High  

### Preconditions
- Transfer `txn-200` is already in REVERSED status.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers/txn-200/reverse | HTTP 422 |
| 2 | Assert `errorCode` = "TRANSFER_ALREADY_REVERSED" | |
| 3 | Count `ledger_entries` | No new entries |

### Expected Outcome
HTTP 422 `TRANSFER_ALREADY_REVERSED`; no new ledger entries.

---

## TC-050: Reversal rejected — transaction in FAILED status

**REQ-ID(s):** REQ-F-026  
**Test Level:** Integration  
**Technique:** STT, DT (DT-REV-03), EP (EP-REV-03)  
**Priority:** High  

### Preconditions
- Transfer `txn-300` has status FAILED.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers/txn-300/reverse | HTTP 422 |
| 2 | Assert `errorCode` = "TRANSFER_NOT_REVERSIBLE" | |

### Expected Outcome
HTTP 422 `TRANSFER_NOT_REVERSIBLE`.

---

## TC-051: Reversal rejected — receiver insufficient funds

**REQ-ID(s):** REQ-F-025  
**Test Level:** Integration  
**Technique:** DT (DT-REV-05), EP (EP-REV-05)  
**Priority:** High  

### Preconditions
- Transfer `txn-300` is COMPLETED; original receiver B has since spent funds (balance = 0).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | POST /v1/transfers/txn-300/reverse | HTTP 422 |
| 2 | Assert `errorCode` = "INSUFFICIENT_FUNDS_FOR_REVERSAL" | |
| 3 | Query Account B balance | Still 0; unchanged |
| 4 | Count `ledger_entries` | No new entries |

### Expected Outcome
HTTP 422 `INSUFFICIENT_FUNDS_FOR_REVERSAL`; no mutations.

---

## TC-052: Reversal links to original transaction via reversal_of_entry_id

**REQ-ID(s):** REQ-F-027  
**Test Level:** Integration  
**Technique:** DT (DT-REV-06)  
**Priority:** Critical  

### Preconditions
- Successful reversal executed (see TC-048).

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Query reversal ledger entries | 2 rows with `reversal_of_entry_id` non-null |
| 2 | Assert `reversal_of_entry_id` values match original DEBIT and CREDIT entry IDs | FK integrity |
| 3 | Query `transactions.reversal_of_transaction_id` for reversal txn | = original `txn-100` UUID |

### Expected Outcome
Reversal entries explicitly link to original entries via `reversal_of_entry_id`; transaction links via `reversal_of_transaction_id`.

---

## Section J — Non-Functional Requirements

---

## TC-053: Structured JSON error envelope on all error responses

**REQ-ID(s):** REQ-NF-009, REQ-NF-010  
**Test Level:** System / Contract  
**Technique:** DT  
**Priority:** High  

### Preconditions
- Each error scenario tested (TC-002 through TC-010) as a sub-case.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Trigger any error response | HTTP 4xx or 5xx |
| 2 | Assert response body contains `errorCode` | Non-null string |
| 3 | Assert response body contains `message` | Non-null, descriptive string |
| 4 | Assert response body contains `traceId` | Non-null UUID/hex string |

### Expected Outcome
All error responses conform to the standard error envelope schema with `errorCode`, `message`, and `traceId`.

---

## TC-054: Health liveness and readiness endpoints return 200

**REQ-ID(s):** CON-008  
**Test Level:** Integration  
**Technique:** EP  
**Priority:** Medium  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /health/liveness | HTTP 200 |
| 2 | GET /health/readiness | HTTP 200 |
| 3 | Assert readiness response includes DB datasource indicator UP | DB connection verified |

### Expected Outcome
Both probes return HTTP 200; readiness confirms DB connectivity.

---

## TC-055: OpenAPI spec available and valid

**REQ-ID(s):** REQ-NF-018  
**Test Level:** System / Contract  
**Technique:** EP  
**Priority:** Low  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | GET /v1/openapi.json | HTTP 200 |
| 2 | Assert response `Content-Type: application/json` | |
| 3 | Validate response against OpenAPI 3.x schema validator | Zero validation errors |

### Expected Outcome
OpenAPI spec is published, accessible without authentication, and validates against OpenAPI 3.x.

---

## TC-056: Ledger entries include initiating_user_id and request_timestamp

**REQ-ID(s):** REQ-NF-010  
**Test Level:** Integration  
**Technique:** DT  
**Priority:** High  

### Preconditions
- Successful transfer with `X-User-Id: user-uuid-001`.

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Execute transfer with known `X-User-Id` | HTTP 201 |
| 2 | Query `ledger_entries` for the transaction | 2 rows |
| 3 | Assert `initiating_user_id` = "user-uuid-001" for both rows | User ID captured |
| 4 | Assert `request_timestamp` non-null and within test window | Timestamp set |

### Expected Outcome
Both ledger entries carry the authenticated user ID and request timestamp — satisfying non-repudiation requirement.

---

## TC-057: Unit and integration test coverage ≥ 90% on transfer and ledger packages

**REQ-ID(s):** REQ-NF-013  
**Test Level:** Meta / Build  
**Technique:** EP  
**Priority:** High  

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | Run Maven/Gradle test with JaCoCo coverage plugin | Coverage report generated |
| 2 | Assert line coverage on `transfer` package ≥ 90% | ≥ 90% |
| 3 | Assert line coverage on `ledger` package ≥ 90% | ≥ 90% |

### Expected Outcome
JaCoCo reports ≥ 90% line coverage on both critical packages.

---

## Appendix: Test Case Index

| TC-ID | Title | Technique | Level | REQ-IDs | Priority |
|---|---|---|---|---|---|
| TC-001 | Successful transfer with sufficient balance | EP | Integration | REQ-F-001, F-006, F-015, F-017 | Critical |
| TC-002 | Transfer rejected — insufficient funds | EP, BVA | Integration | REQ-F-002 | Critical |
| TC-003 | Transfer rejected — amount is zero | BVA, EP | Unit+Integration | REQ-F-003 | Critical |
| TC-004 | Transfer rejected — negative amount | BVA, EP | Unit+Integration | REQ-F-003 | Critical |
| TC-005 | Transfer rejected — self-transfer | EP, DT | Unit+Integration | REQ-F-004 | High |
| TC-006 | Transfer rejected — missing Idempotency-Key header | EP, DT | Integration | REQ-F-007 | High |
| TC-007 | Transfer with exact balance (boundary — succeeds) | BVA | Integration | REQ-F-001, F-002 | Critical |
| TC-008 | Transfer at balance+1 cent (boundary — fails) | BVA | Integration | REQ-F-002, F-013 | Critical |
| TC-009 | Transfer amount of 1 cent (minimum valid) | BVA | Integration | REQ-F-001, F-003 | High |
| TC-010 | Sender account does not exist | EP | Integration | REQ-F-001 | High |
| TC-011 | Ledger entries contain all required fields | DT | Integration | REQ-F-015 | Critical |
| TC-012 | Ledger entries are immutable — UPDATE rejected | DT | Integration | REQ-F-016 | Critical |
| TC-013 | Net-zero invariant holds after transfer | DT | Integration | REQ-F-017 | Critical |
| TC-014 | Rollback on validation failure — zero ledger entries | DT | Integration | REQ-F-015, F-016 | Critical |
| TC-015 | LedgerIntegrityException when DEBIT ≠ CREDIT | DT | Unit | REQ-F-017 | Critical |
| TC-016 | 0.1 + 0.2 = 0.3 exactly (no floating-point trap) | BVA | Unit | REQ-F-005 | Critical |
| TC-017 | Penny split across 3 recipients — no cent lost | BVA | Unit | REQ-F-005 | Critical |
| TC-018 | Minimum representable unit (0.0001) transfers correctly | BVA | Integration | REQ-F-005 | High |
| TC-019 | Scale violation — amount with > 4 dp rejected | BVA | Integration | REQ-F-005 | High |
| TC-020 | BigDecimal(double) constructor never used | BVA | Unit | REQ-F-005 | Critical |
| TC-021 | Idempotent replay of completed transfer | EP, DT | Integration | REQ-F-008 | Critical |
| TC-022 | In-progress transfer returns HTTP 409 | EP, DT | Integration | REQ-F-009 | Critical |
| TC-023 | Idempotency key reused with different payload — 422 | EP, DT | Integration | REQ-F-010 | Critical |
| TC-024 | Retry of failed transfer with same key re-executes | EP, DT | Integration | REQ-F-008 | High |
| TC-025 | Idempotency key survives service restart | DT | Integration | REQ-F-011, REQ-NF-015 | High |
| TC-026 | Two concurrent same-key requests — exactly one succeeds | EP, DT | Integration | REQ-F-007, F-009 | Critical |
| TC-027 | Two concurrent debits — only one succeeds | EP, STT | Integration | REQ-F-012, F-013 | Critical |
| TC-028 | DB-level CHECK constraint prevents negative balance | BVA, DT | Integration | REQ-F-013 | Critical |
| TC-029 | Deadlock retry succeeds on second attempt | STT | Unit | REQ-F-014 | Critical |
| TC-030 | Deadlock exhaustion after 3 retries → HTTP 503 | STT | Unit+Integration | REQ-F-014 | High |
| TC-031 | Lock acquisition order — lower UUID acquired first | DT | Unit | REQ-F-012 | High |
| TC-032 | Concurrent transfers (10 threads) — balance never negative | EP | Integration | REQ-F-012, F-013 | Critical |
| TC-033 | Mid-flight failure before event publish — retry safe | STT | Unit | REQ-F-014, F-008 | Critical |
| TC-034 | Balance inquiry returns current balance | EP | Integration | REQ-F-018 | High |
| TC-035 | Balance inquiry for non-existent account → 404 | EP | Integration | REQ-F-018 | High |
| TC-036 | Point-in-time balance inquiry (asOf parameter) | EP | Integration | REQ-F-019 | Medium |
| TC-037 | Paginated transaction history — first page | BVA, EP | Integration | REQ-F-020 | High |
| TC-038 | Paginated transaction history — last page (partial) | BVA | Integration | REQ-F-020 | High |
| TC-039 | Empty transaction history for new account | EP | Integration | REQ-F-020 | High |
| TC-040 | Transaction history filtered by date range | EP | Integration | REQ-F-021 | Medium |
| TC-041 | Transaction history filtered by type DEBIT | EP | Integration | REQ-F-022 | Medium |
| TC-042 | PageSize exceeds maximum (101) — rejected | BVA | Integration | REQ-F-020 | Medium |
| TC-043 | Query completed transfer by ID | EP | Integration | REQ-F-023, F-024 | High |
| TC-044 | Query non-existent transfer → 404 | EP | Integration | REQ-F-023 | High |
| TC-045 | Query FAILED transfer by ID | EP, STT | Integration | REQ-F-023, F-024 | Medium |
| TC-046 | PENDING → COMPLETED state transition | STT | Integration | REQ-F-024 | Critical |
| TC-047 | PENDING → FAILED state transition | STT | Integration | REQ-F-024 | High |
| TC-048 | COMPLETED → REVERSED (successful reversal) | STT, DT | Integration | REQ-F-024, F-025, F-027 | Critical |
| TC-049 | Reversal rejected — already REVERSED | STT, DT, EP | Integration | REQ-F-026 | High |
| TC-050 | Reversal rejected — FAILED status | STT, DT, EP | Integration | REQ-F-026 | High |
| TC-051 | Reversal rejected — receiver insufficient funds | DT, EP | Integration | REQ-F-025 | High |
| TC-052 | Reversal links to original via reversal_of_entry_id | DT | Integration | REQ-F-027 | Critical |
| TC-053 | Structured JSON error envelope on all errors | DT | System/Contract | REQ-NF-009, F-010 | High |
| TC-054 | Health liveness and readiness endpoints | EP | Integration | CON-008 | Medium |
| TC-055 | OpenAPI spec available and valid | EP | System/Contract | REQ-NF-018 | Low |
| TC-056 | Ledger entries include user ID and timestamp | DT | Integration | REQ-NF-010 | High |
| TC-057 | Test coverage ≥ 90% on transfer and ledger packages | EP | Meta/Build | REQ-NF-013 | High |
