# Feature: Idempotency
# Capability area: Idempotent Transfer Execution
# Linked REQ-IDs: REQ-F-007, REQ-F-008, REQ-F-009, REQ-F-010, REQ-F-011, REQ-NF-015
# User Stories: US-002
# Test Framework: Cucumber 7, JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), AssertJ

@feature-idempotency
Feature: Idempotency
  As a sender whose network connection dropped
  I want to retry my transfer request using the same idempotency key
  So that my money is moved exactly once regardless of how many times the request is sent

  Background:
    Given the payment service is running with a clean PostgreSQL schema
    And account "SENDER" exists with a balance of 10000 cents
    And account "RECEIVER" exists with a balance of 5000 cents

  # ---------------------------------------------------------------------------
  # Idempotency-Key required on every request (REQ-F-007)
  # ---------------------------------------------------------------------------

  @REQ-F-007 @TC-006
  Scenario: Transfer rejected when Idempotency-Key header is absent
    When the sender submits a transfer of "10.0000" USD without an Idempotency-Key header
    Then the API returns HTTP status 400
    And the response errorCode is "MISSING_IDEMPOTENCY_KEY"
    And no ledger entries are created for this request

  # ---------------------------------------------------------------------------
  # Replay of completed transfer returns original response (REQ-F-008)
  # ---------------------------------------------------------------------------

  @REQ-F-008 @TC-021
  Scenario: Idempotent replay of completed transfer returns original response
    Given a transfer of "25.0000" USD was successfully completed with idempotency key "idk-tc021"
    And the original transactionId is recorded
    When the sender retries the same request with key "idk-tc021" and amount "25.0000"
    Then the API returns HTTP status 200
    And the response transactionId matches the original transactionId
    And the response status is "COMPLETED"
    And no new ledger entries are created for the sender account
    And the sender balance is unchanged from after the first transfer
    And the receiver balance is unchanged from after the first transfer

  # ---------------------------------------------------------------------------
  # In-progress transfer returns HTTP 409 (REQ-F-009)
  # ---------------------------------------------------------------------------

  @REQ-F-009 @TC-022
  Scenario: In-progress transfer returns HTTP 409 Conflict
    Given an idempotency key "idk-tc022" exists in the database with status "PENDING"
    When the sender submits a transfer of "10.0000" USD with key "idk-tc022"
    Then the API returns HTTP status 409
    And the response errorCode is "TRANSFER_IN_PROGRESS"
    And no ledger entries are created for this request

  # ---------------------------------------------------------------------------
  # Key reused with different payload returns HTTP 422 (REQ-F-010)
  # ---------------------------------------------------------------------------

  @REQ-F-010 @TC-023
  Scenario: Idempotency key reused with different payload is rejected
    Given a transfer of "100.0000" USD was successfully completed with idempotency key "idk-tc023"
    When the sender retries with key "idk-tc023" but amount "200.0000"
    Then the API returns HTTP status 422
    And the response errorCode is "IDEMPOTENCY_KEY_CONFLICT"
    And no new ledger entries are created for the sender account

  # ---------------------------------------------------------------------------
  # Retry of a FAILED transfer re-executes (REQ-F-008, EP-IDK-06)
  # ---------------------------------------------------------------------------

  @REQ-F-008 @TC-024
  Scenario: Retry of failed transfer with same key and payload re-executes the transfer
    Given an idempotency key "idk-tc024" exists in the database with status "FAILED"
    And the payload hash for "idk-tc024" matches the current request
    When the sender submits a transfer of "10.0000" USD with key "idk-tc024"
    Then the API returns HTTP status 201
    And a new transactionId is returned
    And exactly 2 new ledger entries exist for the new transaction
    And the net sum of ledger entries for the new transaction equals zero

  # ---------------------------------------------------------------------------
  # Two concurrent requests with same key — exactly one proceeds (concurrent EP-IDK)
  # ---------------------------------------------------------------------------

  @REQ-F-007 @REQ-F-009 @TC-026
  Scenario: Two concurrent requests with the same idempotency key — exactly one succeeds
    Given idempotency key "idk-tc026" has never been used
    When 2 concurrent threads simultaneously submit a transfer of "50.0000" USD with key "idk-tc026"
    Then exactly 1 thread receives HTTP status 201
    And exactly 1 thread receives HTTP status 409 with errorCode "TRANSFER_IN_PROGRESS"
    And only 2 ledger entries exist for the sender account in total
    And the sender balance is decremented exactly once

  # ---------------------------------------------------------------------------
  # TTL boundary: key older than 24 hours is eligible for cleanup (REQ-F-011)
  # ---------------------------------------------------------------------------

  @REQ-F-011 @TC-025-ttl
  Scenario Outline: Idempotency key cleanup eligibility based on age
    Given an idempotency key "idk-ttl-<case>" was created "<age_description>" ago
    When the scheduled cleanup task runs
    Then the key "<key_status>" eligible for deletion

    Examples:
      | case | age_description    | key_status |
      | 01   | exactly 24 hours   | is         |
      | 02   | 24 hours + 1 second| is         |
      | 03   | 23 hours 59 minutes| is not     |

  # ---------------------------------------------------------------------------
  # Idempotency key survives service restart (REQ-F-011, REQ-NF-015)
  # ---------------------------------------------------------------------------

  @REQ-F-011 @REQ-NF-015 @TC-025
  Scenario: Idempotency key record survives a service restart
    Given a transfer of "20.0000" USD was successfully completed with idempotency key "idk-tc025"
    And the original transactionId is recorded
    When the application context is restarted and the retry is submitted with key "idk-tc025"
    Then the API returns HTTP status 200
    And the response transactionId matches the original transactionId
    And no new ledger entries are created

  # ---------------------------------------------------------------------------
  # Idempotency key dispatch decision table (DT-IDK) — combined Scenario Outline
  # ---------------------------------------------------------------------------

  @REQ-F-007 @REQ-F-008 @REQ-F-009 @REQ-F-010
  Scenario Outline: Idempotency key dispatch matrix
    Given the idempotency key "<key>" has state "<key_state>" in the database
    And the request payload hash "<hash_match>" the stored hash
    When the sender submits a transfer request with key "<key>" and amount "<amount>"
    Then the API returns HTTP status <expected_status>
    And the response errorCode is "<expected_error_code>"

    Examples:
      | key        | key_state | hash_match      | amount  | expected_status | expected_error_code             |
      | idk-dt-02  | NEW       | N/A             | 25.0000 | 201             | N/A                             |
      | idk-dt-03  | PENDING   | N/A             | 25.0000 | 409             | TRANSFER_IN_PROGRESS            |
      | idk-dt-04  | COMPLETED | matches         | 25.0000 | 200             | N/A                             |
      | idk-dt-05  | COMPLETED | does not match  | 50.0000 | 422             | IDEMPOTENCY_KEY_CONFLICT        |
      | idk-dt-07  | FAILED    | does not match  | 50.0000 | 422             | IDEMPOTENCY_KEY_CONFLICT        |
