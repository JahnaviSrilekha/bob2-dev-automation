# Feature: Balance Safety
# Capability area: Concurrency Control, Balance Protection, Balance Inquiry,
#                  BigDecimal Arithmetic, DB Constraints
# Linked REQ-IDs: REQ-F-012, REQ-F-013, REQ-F-014, REQ-F-018, REQ-F-019,
#                 REQ-F-005 (precision), REQ-F-015, REQ-F-017
# User Stories: US-003, US-007
# Test Framework: Cucumber 7, JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL),
#                 AssertJ, CountDownLatch, ExecutorService

@feature-balance-safety
Feature: Balance Safety
  As a platform engineer and end user
  I want the payment service to prevent negative balances under any concurrent load
  And ensure all monetary arithmetic is exact with no floating-point rounding errors
  So that every account balance is always accurate and auditable

  Background:
    Given the payment service is running with a clean PostgreSQL schema

  # ---------------------------------------------------------------------------
  # Two concurrent debits — only one succeeds when balance allows only one
  # (REQ-F-012, REQ-F-013 — US-007 Scenario 1)
  # ---------------------------------------------------------------------------

  @REQ-F-012 @REQ-F-013 @TC-027
  Scenario: Two concurrent debits — exactly one succeeds when balance allows only one
    Given account "ACCOUNT_A" exists with a balance of 1000 cents
    And account "ACCOUNT_B" exists with a balance of 0 cents
    And account "ACCOUNT_C" exists with a balance of 0 cents
    When 2 concurrent threads simultaneously attempt to debit 800 cents from "ACCOUNT_A"
    Then exactly 1 transfer succeeds with HTTP status 201
    And exactly 1 transfer fails with HTTP status 422 and errorCode "INSUFFICIENT_FUNDS"
    And the final balance of "ACCOUNT_A" is exactly 200 cents
    And the total number of ledger entries for "ACCOUNT_A" is exactly 2
    And the net sum of ledger entries for the successful transaction equals zero

  # ---------------------------------------------------------------------------
  # 10-thread concurrent debit stress test — balance never negative
  # @RepeatedTest equivalent: run scenario 10 times
  # (REQ-F-012, REQ-F-013)
  # ---------------------------------------------------------------------------

  @REQ-F-012 @REQ-F-013 @TC-032
  Scenario: 10 concurrent debit threads never produce a negative balance
    Given account "ACCOUNT_STRESS" exists with a balance of 10000 cents
    And 10 receiver accounts exist
    When 10 concurrent threads each attempt to debit 1500 cents from "ACCOUNT_STRESS" simultaneously
    Then the final balance of "ACCOUNT_STRESS" is greater than or equal to 0 cents
    And the final balance of "ACCOUNT_STRESS" is less than 10000 cents
    And the number of successful transfers multiplied by 1500 equals the total amount debited from "ACCOUNT_STRESS"
    And the net sum of ledger entries for every successful transaction equals zero

  # ---------------------------------------------------------------------------
  # Database CHECK constraint prevents negative balance via direct SQL
  # (REQ-F-013)
  # ---------------------------------------------------------------------------

  @REQ-F-013 @TC-028
  Scenario: Database-level CHECK constraint rejects direct negative balance update
    Given account "ACCOUNT_ZERO" exists with a balance of 0 cents
    When a direct SQL UPDATE attempts to set "ACCOUNT_ZERO" balance to -0.0001
    Then a DataIntegrityViolationException is thrown
    And the balance of "ACCOUNT_ZERO" remains 0 cents

  # ---------------------------------------------------------------------------
  # Deadlock retry — succeeds on second attempt (REQ-F-014, STT-DLK-04)
  # ---------------------------------------------------------------------------

  @REQ-F-014 @TC-029
  Scenario: Deadlock retry succeeds within retry budget
    Given a deadlock scenario where the lock acquisition fails on the first attempt
    And the lock acquisition succeeds on the second attempt
    When the transfer service processes the transfer with amount "10.0000"
    Then the transfer completes successfully with status "COMPLETED"
    And the lock repository was called exactly 2 times
    And exactly 2 ledger entries are written (one transfer only)

  # ---------------------------------------------------------------------------
  # Deadlock retry exhaustion — HTTP 503 (REQ-F-014, STT-DLK-03)
  # ---------------------------------------------------------------------------

  @REQ-F-014 @TC-030
  Scenario: Deadlock exhaustion after 3 retries returns HTTP 503
    Given account "ACCOUNT_DEADLOCK" exists with a balance of 10000 cents
    And account "RECEIVER_DEADLOCK" exists
    And the lock acquisition always fails with CannotAcquireLockException
    When the transfer is submitted for "10.0000" USD
    Then the API returns HTTP status 503
    And the response errorCode is "TRANSFER_DEADLOCK_EXHAUSTED"
    And no ledger entries are created

  # ---------------------------------------------------------------------------
  # Lock acquisition order — lower UUID always locked first (REQ-F-012)
  # ---------------------------------------------------------------------------

  @REQ-F-012 @TC-031
  Scenario: Lock acquisition order uses ascending UUID regardless of sender/receiver direction
    Given account "ACCOUNT_HIGH_UUID" exists with UUID "ffffffff-ffff-ffff-ffff-ffffffffffff" and balance 10000 cents
    And account "ACCOUNT_LOW_UUID" exists with UUID "00000000-0000-0000-0000-000000000001" and balance 0 cents
    When "ACCOUNT_HIGH_UUID" submits a transfer to "ACCOUNT_LOW_UUID" for "50.0000" USD
    Then the lock is acquired on UUID "00000000-0000-0000-0000-000000000001" first
    And the lock SQL query contains ORDER BY id ascending
    And the transfer completes successfully

  # ---------------------------------------------------------------------------
  # Mid-flight failure before event publish — no double-credit on retry (REQ-F-014, REQ-F-008)
  # ---------------------------------------------------------------------------

  @REQ-F-014 @REQ-F-008 @TC-033
  Scenario: Mid-flight event publish failure does not corrupt committed ledger entries
    Given account "ACCOUNT_SENDER" exists with a balance of 10000 cents
    And account "ACCOUNT_RCVR" exists
    And the event publisher is configured to throw an exception
    And idempotency key "idk-tc033" is new
    When the transfer of "50.0000" USD is submitted with key "idk-tc033"
    Then the API returns HTTP status 201 (DB committed before event publish)
    And the transfer status in the database is "COMPLETED"
    And exactly 2 ledger entries exist for the transaction
    When the same request is retried with key "idk-tc033" and amount "50.0000"
    Then the API returns HTTP status 200
    And the ledger entry count remains exactly 2 (no double-write)
    And the sender balance is decremented exactly once

  # ---------------------------------------------------------------------------
  # Balance Inquiry — current balance (REQ-F-018)
  # ---------------------------------------------------------------------------

  @REQ-F-018 @TC-034
  Scenario: Balance inquiry returns current authoritative balance
    Given account "INQUIRY_ACCOUNT" exists with a balance of 7500 cents
    When the caller requests GET /v1/accounts/{accountId}/balance
    Then the API returns HTTP status 200
    And the response balance is "75.0000"
    And the response currency is "USD"
    And the response accountId matches the requested account

  @REQ-F-018 @TC-035
  Scenario: Balance inquiry for non-existent account returns 404
    Given an account ID that does not exist in the database
    When the caller requests GET /v1/accounts/{unknownId}/balance
    Then the API returns HTTP status 404
    And the response errorCode is "ACCOUNT_NOT_FOUND"

  @REQ-F-019 @TC-036
  Scenario: Point-in-time balance inquiry returns historical balance
    Given account "HISTORY_ACCOUNT" had a balance of 5000 cents at timestamp "2024-01-01T00:00:00Z"
    And a subsequent transfer changed the balance to 3000 cents
    When the caller requests GET /v1/accounts/{accountId}/balance?asOf=2024-01-01T00:00:00Z
    Then the API returns HTTP status 200
    And the response balance is "50.0000"
    And the response asOf is "2024-01-01T00:00:00Z"

  # ---------------------------------------------------------------------------
  # BigDecimal precision — floating-point traps (REQ-F-005, CON-003)
  # ---------------------------------------------------------------------------

  @REQ-F-005 @TC-016
  Scenario: 0.1 + 0.2 equals exactly 0.3 with no floating-point error
    Given the monetary arithmetic helper is loaded
    When a credit of "0.1000" is applied to a zero balance
    And a credit of "0.2000" is applied to the resulting balance
    And a debit of "0.3000" is applied to the resulting balance
    Then the resulting balance is exactly "0.0000"
    And the result has scale exactly 4
    And the result does not equal 0.30000000000000004 (floating-point trap avoided)

  @REQ-F-005 @TC-017
  Scenario: Penny split across 3 recipients produces parts that sum to original
    Given the monetary arithmetic helper is loaded
    When a total amount of "10.0000" is divided into 3 equal parts using HALF_EVEN rounding
    Then each part is "3.3333"
    And the computed remainder is "0.0001"
    And the sum of 3 parts plus the remainder equals "10.0000"
    And no part has scale greater than 4

  @REQ-F-005 @TC-018
  Scenario: Transfer of minimum representable unit (0.0001) succeeds without precision loss
    Given account "MIN_SENDER" exists with a balance of 10000 cents
    And account "MIN_RECEIVER" exists
    And a unique idempotency key "idk-tc018"
    When the sender submits a transfer of "0.0001" USD with key "idk-tc018"
    Then the API returns HTTP status 201
    And the sender balance is "9999.9999"
    And both ledger entries have amount "0.0001"
    And the net sum of ledger entries for the transaction equals zero

  # ---------------------------------------------------------------------------
  # Transaction Status — query by ID (REQ-F-023, REQ-F-024)
  # ---------------------------------------------------------------------------

  @REQ-F-023 @REQ-F-024 @TC-043
  Scenario: Query completed transfer by transaction ID
    Given a transfer was completed successfully with a known transactionId
    When the caller requests GET /v1/transfers/{transactionId}
    Then the API returns HTTP status 200
    And the response status is "COMPLETED"
    And the response amount matches the original transfer amount
    And the response senderAccountId and receiverAccountId are present
    And the response createdAt is non-null

  @REQ-F-023 @TC-044
  Scenario: Query non-existent transfer returns 404
    Given a transactionId that does not exist in the database
    When the caller requests GET /v1/transfers/{transactionId}
    Then the API returns HTTP status 404
    And the response errorCode is "TRANSACTION_NOT_FOUND"

  # ---------------------------------------------------------------------------
  # Transaction Status state machine — all statuses queryable (REQ-F-024)
  # ---------------------------------------------------------------------------

  @REQ-F-024 @TC-046 @TC-047 @TC-045
  Scenario Outline: Transaction status endpoint returns correct status for each state
    Given a transaction exists with status "<status>"
    When the caller requests GET /v1/transfers/{transactionId}
    Then the API returns HTTP status 200
    And the response status is "<status>"

    Examples:
      | status    |
      | COMPLETED |
      | FAILED    |
      | REVERSED  |

  # ---------------------------------------------------------------------------
  # Non-functional: all error responses include traceId (REQ-NF-009)
  # ---------------------------------------------------------------------------

  @REQ-NF-009 @TC-053
  Scenario Outline: All error responses include errorCode, message, and traceId
    Given account "ERR_SENDER" exists with a balance of 100 cents
    When the "<error_scenario>" is triggered
    Then the error response contains field "errorCode" with value "<expected_code>"
    And the error response contains field "message" that is non-empty
    And the error response contains field "traceId" that is non-null

    Examples:
      | error_scenario                     | expected_code               |
      | insufficient funds transfer        | INSUFFICIENT_FUNDS          |
      | zero amount transfer               | INVALID_AMOUNT              |
      | self-transfer                      | SELF_TRANSFER_NOT_ALLOWED   |
      | missing idempotency key            | MISSING_IDEMPOTENCY_KEY     |
      | non-existent account balance query | ACCOUNT_NOT_FOUND           |
      | non-existent transaction status    | TRANSACTION_NOT_FOUND       |
