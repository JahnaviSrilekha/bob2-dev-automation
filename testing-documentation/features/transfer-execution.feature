# Feature: Transfer Execution
# Capability area: POST /v1/transfers — Initiate Transfer
# Linked REQ-IDs: REQ-F-001, REQ-F-002, REQ-F-003, REQ-F-004, REQ-F-005, REQ-F-006,
#                 REQ-F-015, REQ-F-016, REQ-F-017
# User Stories: US-001, US-001a, US-001b
# Test Framework: Cucumber 7, JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), AssertJ

@feature-transfer-execution
Feature: Transfer Execution
  As a sender
  I want to transfer a specified amount to another user
  So that funds move atomically, a transaction ID is returned, and double-entry ledger entries are created

  Background:
    Given the payment service is running with a clean PostgreSQL schema
    And account "SENDER" exists with a balance of 10000 cents
    And account "RECEIVER" exists with a balance of 5000 cents

  # ---------------------------------------------------------------------------
  # Happy-path transfer
  # ---------------------------------------------------------------------------

  @REQ-F-001 @REQ-F-006 @REQ-F-015 @REQ-F-017 @TC-001
  Scenario: Successful transfer with sufficient balance
    Given a unique idempotency key "idk-tc001"
    When the sender submits a transfer of "25.0000" USD to the receiver with key "idk-tc001"
    Then the API returns HTTP status 201
    And the response contains a non-null transactionId
    And the response status is "COMPLETED"
    And the response amount is "25.0000"
    And the sender balance is 7500 cents
    And the receiver balance is 7500 cents
    And exactly 2 ledger entries exist for the transaction
    And one ledger entry has type "DEBIT" for account "SENDER"
    And one ledger entry has type "CREDIT" for account "RECEIVER"
    And the net sum of ledger entries for the transaction equals zero

  # ---------------------------------------------------------------------------
  # BVA: exact-balance boundary
  # ---------------------------------------------------------------------------

  @REQ-F-001 @REQ-F-002 @TC-007
  Scenario: Transfer with exact balance succeeds and leaves balance at zero
    Given a unique idempotency key "idk-tc007"
    When the sender submits a transfer of "100.0000" USD to the receiver with key "idk-tc007"
    Then the API returns HTTP status 201
    And the sender balance is 0 cents
    And the accounts balance CHECK constraint is not violated
    And the net sum of ledger entries for the transaction equals zero

  # ---------------------------------------------------------------------------
  # BVA: minimum valid amount (1 cent)
  # ---------------------------------------------------------------------------

  @REQ-F-001 @REQ-F-003 @TC-009
  Scenario: Transfer of minimum unit (1 cent) succeeds
    Given a unique idempotency key "idk-tc009"
    When the sender submits a transfer of "0.0100" USD to the receiver with key "idk-tc009"
    Then the API returns HTTP status 201
    And the sender balance is 9999 cents
    And the net sum of ledger entries for the transaction equals zero

  # ---------------------------------------------------------------------------
  # Insufficient funds — multiple amounts via Scenario Outline (EP + BVA)
  # ---------------------------------------------------------------------------

  @REQ-F-002 @TC-002 @TC-008
  Scenario Outline: Transfer rejected when amount exceeds sender balance
    Given a unique idempotency key "<key>"
    When the sender submits a transfer of "<amount>" USD to the receiver with key "<key>"
    Then the API returns HTTP status 422
    And the response errorCode is "INSUFFICIENT_FUNDS"
    And the response contains a traceId
    And no ledger entries are created for this request
    And the sender balance is unchanged at 10000 cents

    Examples:
      | key       | amount      | note                     |
      | idk-tc002 | 50.0000     | amount > balance (TC-002) |
      | idk-tc008 | 100.0100    | balance+1 cent (TC-008)   |

  # ---------------------------------------------------------------------------
  # Zero and negative amounts (EP + BVA)
  # ---------------------------------------------------------------------------

  @REQ-F-003 @TC-003 @TC-004
  Scenario Outline: Transfer rejected for zero or negative amount
    Given a unique idempotency key "<key>"
    When the sender submits a transfer of "<amount>" USD to the receiver with key "<key>"
    Then the API returns HTTP status 400
    And the response errorCode is "INVALID_AMOUNT"
    And no ledger entries are created for this request

    Examples:
      | key       | amount  | note              |
      | idk-tc003 | 0.0000  | zero (TC-003)     |
      | idk-tc004 | -0.0100 | negative (TC-004) |

  # ---------------------------------------------------------------------------
  # Self-transfer (DT-TRF-04)
  # ---------------------------------------------------------------------------

  @REQ-F-004 @TC-005
  Scenario: Transfer rejected when sender and receiver are the same account
    Given a unique idempotency key "idk-tc005"
    When the sender submits a transfer of "10.0000" USD to themselves with key "idk-tc005"
    Then the API returns HTTP status 422
    And the response errorCode is "SELF_TRANSFER_NOT_ALLOWED"
    And no ledger entries are created for this request
    And the sender balance is unchanged at 10000 cents

  # ---------------------------------------------------------------------------
  # Missing Idempotency-Key header (DT-TRF-01)
  # ---------------------------------------------------------------------------

  @REQ-F-007 @TC-006
  Scenario: Transfer rejected when Idempotency-Key header is absent
    When the sender submits a transfer of "10.0000" USD without an Idempotency-Key header
    Then the API returns HTTP status 400
    And the response errorCode is "MISSING_IDEMPOTENCY_KEY"

  # ---------------------------------------------------------------------------
  # Sender account does not exist (EP-ACC-02)
  # ---------------------------------------------------------------------------

  @REQ-F-001 @TC-010
  Scenario: Transfer rejected when sender account does not exist
    Given a unique idempotency key "idk-tc010"
    And a sender account ID that does not exist in the database
    When the unknown sender submits a transfer of "10.0000" USD with key "idk-tc010"
    Then the API returns HTTP status 404
    And the response errorCode is "ACCOUNT_NOT_FOUND"

  # ---------------------------------------------------------------------------
  # Scale violation — amount with > 4 decimal places (BVA-BD-04)
  # ---------------------------------------------------------------------------

  @REQ-F-005 @TC-019
  Scenario: Transfer rejected for amount with more than 4 decimal places
    Given a unique idempotency key "idk-tc019"
    When the sender submits a transfer of "0.00001" USD to the receiver with key "idk-tc019"
    Then the API returns HTTP status 400
    And the response errorCode is "INVALID_AMOUNT"

  # ---------------------------------------------------------------------------
  # Double-entry ledger — all required fields present (REQ-F-015)
  # ---------------------------------------------------------------------------

  @REQ-F-015 @TC-011
  Scenario: Ledger entries contain all required fields after successful transfer
    Given a unique idempotency key "idk-tc011"
    And the X-User-Id header is "user-uuid-001"
    When the sender submits a transfer of "25.0000" USD to the receiver with key "idk-tc011"
    Then the API returns HTTP status 201
    And both ledger entries have a non-null account_id
    And both ledger entries have a valid entry_type of "DEBIT" or "CREDIT"
    And both ledger entries have amount "25.0000"
    And both ledger entries have currency "USD"
    And both ledger entries have a non-null request_timestamp
    And both ledger entries have initiating_user_id "user-uuid-001"
    And both ledger entries have the returned transaction_id

  # ---------------------------------------------------------------------------
  # Double-entry net-zero across multiple transfers (REQ-F-017)
  # ---------------------------------------------------------------------------

  @REQ-F-017 @TC-013
  Scenario Outline: Net-zero invariant holds for each transfer amount
    Given a unique idempotency key "<key>"
    And the sender has sufficient balance for amount "<amount>"
    When the sender submits a transfer of "<amount>" USD to the receiver with key "<key>"
    Then the API returns HTTP status 201
    And the net sum of ledger entries for the transaction equals zero

    Examples:
      | key         | amount      |
      | idk-tc013a  | 25.0000     |
      | idk-tc013b  | 0.0100      |
      | idk-tc013c  | 999.9999    |

  # ---------------------------------------------------------------------------
  # Rollback on validation failure — zero ledger entries (REQ-F-015, DT-LED-02)
  # ---------------------------------------------------------------------------

  @REQ-F-015 @REQ-F-016 @TC-014
  Scenario: No ledger entries created when transfer fails validation
    Given account "LOW_BALANCE_SENDER" exists with a balance of 100 cents
    And a unique idempotency key "idk-tc014"
    When the LOW_BALANCE_SENDER submits a transfer of "100.0000" USD to the receiver with key "idk-tc014"
    Then the API returns HTTP status 422
    And no ledger entries are created for this request

  # ---------------------------------------------------------------------------
  # Reversal flow — COMPLETED → REVERSED (REQ-F-024, REQ-F-025, REQ-F-027)
  # ---------------------------------------------------------------------------

  @REQ-F-024 @REQ-F-025 @REQ-F-027 @TC-048
  Scenario: Successful reversal of a completed transfer
    Given a unique idempotency key "idk-tc048"
    And the sender submits a transfer of "50.0000" USD to the receiver with key "idk-tc048"
    And the transfer completes with status "COMPLETED"
    And the receiver still has the 5000 cents from the original transfer in their balance
    When an authorised caller submits a reversal for the completed transaction
    Then the API returns HTTP status 201
    And the response contains a non-null reversalTransactionId
    And the response originalTransactionId matches the original transaction
    And the sender balance is restored by 5000 cents
    And the receiver balance is reduced by 5000 cents
    And 2 new offsetting ledger entries are created for the reversal
    And each reversal ledger entry references the original entry via reversal_of_entry_id
    And the original transaction status is "REVERSED"
    And the net sum of ledger entries for the reversal transaction equals zero

  @REQ-F-026 @TC-049
  Scenario: Reversal rejected when transfer is already REVERSED
    Given a transfer exists with status "REVERSED"
    When an authorised caller submits a reversal for that transaction
    Then the API returns HTTP status 422
    And the response errorCode is "TRANSFER_ALREADY_REVERSED"
    And no new ledger entries are created

  @REQ-F-026 @TC-050
  Scenario: Reversal rejected when transfer is in FAILED status
    Given a transfer exists with status "FAILED"
    When an authorised caller submits a reversal for that transaction
    Then the API returns HTTP status 422
    And the response errorCode is "TRANSFER_NOT_REVERSIBLE"

  @REQ-F-025 @TC-051
  Scenario: Reversal rejected when receiver has insufficient balance
    Given a unique idempotency key "idk-tc051"
    And the sender submits a transfer of "50.0000" USD to the receiver with key "idk-tc051"
    And the transfer completes
    And the receiver has spent all their funds (balance = 0)
    When an authorised caller submits a reversal for the completed transaction
    Then the API returns HTTP status 422
    And the response errorCode is "INSUFFICIENT_FUNDS_FOR_REVERSAL"
    And no new ledger entries are created
    And the receiver balance remains 0 cents
