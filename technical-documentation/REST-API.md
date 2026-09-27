# Payment Service — REST API Reference

**Version:** v1  
**Base URL (local):** `http://localhost:8080`  
**OpenAPI spec:** `GET /v1/openapi.json`  
**Swagger UI:** `GET /v1/swagger-ui.html`  
**Docker Compose:** `payment-service/docker-compose.yml`

---

## Quick Start

```bash
# 1. Start dependencies (PostgreSQL + RabbitMQ)
cd payment-service
docker-compose up -d

# 2. Run the service
mvn spring-boot:run

# 3. Open Swagger UI in browser
open http://localhost:8080/v1/swagger-ui.html
```

---

## Authentication

The API Gateway injects two trusted headers. When testing locally, supply them manually:

| Header | Required on | Value |
|---|---|---|
| `X-User-Id` | All endpoints | Any UUID v4 (represents the caller) |
| `Idempotency-Key` | `POST /v1/transfers` only | Unique UUID v4 per transfer attempt |
| `X-User-Role` | `GET /v1/admin/transactions` only | Must be `ADMIN` |

---

## Endpoints

### 1. Initiate a Transfer

**`POST /v1/transfers`**

Atomically debits the sender and credits the receiver. Safe to retry — identical key + payload returns the original response without re-executing.

**Headers:**
```
Content-Type: application/json
Idempotency-Key: <UUID v4>
X-User-Id: <UUID v4>
```

**Request body:**
```json
{
  "senderAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "receiverAccountId": "7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456",
  "amount": "25.0000",
  "currency": "USD"
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-User-Id: $(uuidgen)" \
  -d '{
    "senderAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "receiverAccountId": "7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456",
    "amount": "25.0000",
    "currency": "USD"
  }'
```

**Response `201 Created`:**
```json
{
  "transactionId": "a1b2c3d4-0000-0000-0000-000000000001",
  "status": "COMPLETED",
  "amount": 25.0000,
  "currency": "USD",
  "createdAt": "2025-01-15T10:00:00Z"
}
```

| Status | Meaning |
|---|---|
| `201` | Transfer executed |
| `200` | Idempotent replay — same key, same payload, no new transfer |
| `400` | Missing/invalid fields or missing `Idempotency-Key` |
| `409` | Transfer with this key is in-progress |
| `422` | `INSUFFICIENT_FUNDS` / `SELF_TRANSFER_NOT_ALLOWED` / `IDEMPOTENCY_KEY_CONFLICT` |
| `503` | Deadlock retry budget exhausted — safe to retry |

---

### 2. Get Transfer Status

**`GET /v1/transfers/{transactionId}`**

Returns the current status and details of any transfer by its ID.

**curl example:**
```bash
curl http://localhost:8080/v1/transfers/a1b2c3d4-0000-0000-0000-000000000001 \
  -H "X-User-Id: $(uuidgen)"
```

**Response `200 OK`:**
```json
{
  "transactionId": "a1b2c3d4-0000-0000-0000-000000000001",
  "status": "COMPLETED",
  "amount": 25.0000,
  "currency": "USD",
  "senderAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "receiverAccountId": "7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456",
  "createdAt": "2025-01-15T10:00:00Z"
}
```

| Status | Meaning |
|---|---|
| `200` | Transaction found |
| `404` | `TRANSACTION_NOT_FOUND` |

---

### 3. Reverse a Completed Transfer

**`POST /v1/transfers/{transactionId}/reverse`**

Creates offsetting journal entries (credit original sender, debit original receiver). The original transaction becomes `REVERSED`. The receiver must have sufficient balance to cover the reversal.

**curl example:**
```bash
curl -X POST \
  http://localhost:8080/v1/transfers/a1b2c3d4-0000-0000-0000-000000000001/reverse \
  -H "X-User-Id: $(uuidgen)"
```

**Response `201 Created`:**
```json
{
  "reversalTransactionId": "f9e8d7c6-0000-0000-0000-000000000002",
  "originalTransactionId": "a1b2c3d4-0000-0000-0000-000000000001",
  "status": "COMPLETED",
  "createdAt": "2025-01-15T11:00:00Z"
}
```

| Status | Meaning |
|---|---|
| `201` | Reversal executed |
| `404` | `TRANSACTION_NOT_FOUND` |
| `422` | `TRANSFER_ALREADY_REVERSED` / `TRANSFER_NOT_REVERSIBLE` / `INSUFFICIENT_FUNDS_FOR_REVERSAL` |

---

### 4. Get Account Balance

**`GET /v1/accounts/{accountId}/balance`**

Returns the current authoritative balance. Append `?asOf=` for a point-in-time balance.

**curl examples:**
```bash
# Current balance
curl http://localhost:8080/v1/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/balance \
  -H "X-User-Id: $(uuidgen)"

# Point-in-time balance
curl "http://localhost:8080/v1/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/balance?asOf=2025-01-01T00:00:00Z" \
  -H "X-User-Id: $(uuidgen)"
```

**Response `200 OK`:**
```json
{
  "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "balance": 75.0000,
  "currency": "USD",
  "asOf": null
}
```

| Status | Meaning |
|---|---|
| `200` | Balance returned |
| `404` | `ACCOUNT_NOT_FOUND` |

---

### 5. Get Transaction History (per account)

**`GET /v1/accounts/{accountId}/transactions`**

Returns a paginated, time-ordered list of ledger entries for a given account.

**Query parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `page` | int | 1 | 1-based page number |
| `pageSize` | int | 20 | Max entries per page |
| `from` | ISO-8601 | — | Filter entries from this timestamp (inclusive) |
| `to` | ISO-8601 | — | Filter entries to this timestamp (inclusive) |
| `type` | `DEBIT` \| `CREDIT` | — | Filter by entry type |

**curl examples:**
```bash
# All history, first page
curl "http://localhost:8080/v1/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/transactions?page=1&pageSize=20" \
  -H "X-User-Id: $(uuidgen)"

# Filtered by date range
curl "http://localhost:8080/v1/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/transactions?from=2025-01-01T00:00:00Z&to=2025-01-31T23:59:59Z" \
  -H "X-User-Id: $(uuidgen)"

# Only DEBIT entries
curl "http://localhost:8080/v1/accounts/3fa85f64-5717-4562-b3fc-2c963f66afa6/transactions?type=DEBIT" \
  -H "X-User-Id: $(uuidgen)"
```

**Response `200 OK`:**
```json
{
  "entries": [
    {
      "ledgerEntryId": "e1000000-0000-0000-0000-000000000001",
      "transactionId": "a1b2c3d4-0000-0000-0000-000000000001",
      "entryType": "DEBIT",
      "amount": 25.0000,
      "currency": "USD",
      "requestTimestamp": "2025-01-15T10:00:00Z",
      "counterpartyAccountId": "7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456"
    }
  ],
  "totalCount": 1,
  "page": 1,
  "pageSize": 20,
  "totalPages": 1
}
```

| Status | Meaning |
|---|---|
| `200` | Page returned (empty list if no entries) |
| `404` | `ACCOUNT_NOT_FOUND` |

---

### 6. Admin — List All Transactions

**`GET /v1/admin/transactions`**  
🔒 **Requires `X-User-Role: ADMIN` header. Returns `403` for any other caller.**

Returns a paginated list of all transactions across all accounts. Used by compliance officers and platform engineers.

**Query parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `page` | int | 1 | 1-based page number |
| `pageSize` | int | 20 | Max entries per page |
| `accountId` | UUID | — | Filter by account (matches sender **or** receiver) |
| `from` | ISO-8601 | — | Filter from this timestamp (inclusive) |
| `to` | ISO-8601 | — | Filter to this timestamp (inclusive) |
| `status` | `PENDING\|COMPLETED\|FAILED\|REVERSED` | — | Filter by transaction status |

**curl examples:**
```bash
# All transactions, first page (admin)
curl "http://localhost:8080/v1/admin/transactions" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Id: $(uuidgen)"

# Filter by account
curl "http://localhost:8080/v1/admin/transactions?accountId=3fa85f64-5717-4562-b3fc-2c963f66afa6" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Id: $(uuidgen)"

# Filter by date range and status
curl "http://localhost:8080/v1/admin/transactions?from=2025-01-01T00:00:00Z&to=2025-01-31T23:59:59Z&status=COMPLETED" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Id: $(uuidgen)"

# Non-admin → 403
curl "http://localhost:8080/v1/admin/transactions" \
  -H "X-User-Role: USER"
```

**Response `200 OK`:**
```json
{
  "entries": [
    {
      "transactionId": "a1b2c3d4-0000-0000-0000-000000000001",
      "senderAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "receiverAccountId": "7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456",
      "amount": 25.0000,
      "currency": "USD",
      "status": "COMPLETED",
      "createdAt": "2025-01-15T10:00:00Z"
    }
  ],
  "totalCount": 1,
  "page": 1,
  "pageSize": 20,
  "totalPages": 1
}
```

| Status | Meaning |
|---|---|
| `200` | Paginated list returned |
| `403` | `FORBIDDEN` — missing or non-ADMIN role header |

---

## Error Response Format

All errors follow the same envelope:

```json
{
  "errorCode": "INSUFFICIENT_FUNDS",
  "message": "Sender balance 10.0000 is less than requested amount 50.0000",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

### Error Code Reference

| HTTP | Error Code | Trigger |
|---|---|---|
| 400 | `INVALID_AMOUNT` | Amount ≤ 0 |
| 400 | `MISSING_IDEMPOTENCY_KEY` | `Idempotency-Key` header absent |
| 400 | `INVALID_REQUEST` | Validation failure on request body |
| 403 | `FORBIDDEN` | Non-admin calling admin endpoint |
| 404 | `ACCOUNT_NOT_FOUND` | Account UUID not in DB |
| 404 | `TRANSACTION_NOT_FOUND` | Transaction UUID not in DB |
| 409 | `TRANSFER_IN_PROGRESS` | Same idempotency key is still processing |
| 422 | `INSUFFICIENT_FUNDS` | Sender balance < transfer amount |
| 422 | `SELF_TRANSFER_NOT_ALLOWED` | Sender UUID == receiver UUID |
| 422 | `IDEMPOTENCY_KEY_CONFLICT` | Same key reused with different payload |
| 422 | `TRANSFER_NOT_REVERSIBLE` | Transfer is not in COMPLETED status |
| 422 | `TRANSFER_ALREADY_REVERSED` | Transfer is already REVERSED |
| 422 | `INSUFFICIENT_FUNDS_FOR_REVERSAL` | Receiver spent the funds since transfer |
| 500 | `LEDGER_INTEGRITY_ERROR` | Net-zero assertion failed (page on-call immediately) |
| 503 | `TRANSFER_DEADLOCK_EXHAUSTED` | 3 pessimistic lock retries failed — safe to retry |

---

## Health Probes (Kubernetes)

```bash
# Liveness — JVM alive
curl http://localhost:8080/health/liveness

# Readiness — DB reachable
curl http://localhost:8080/health/readiness
```

---

## OpenAPI Machine-Readable Spec

```bash
# Download the OpenAPI 3.x JSON spec
curl http://localhost:8080/v1/openapi.json -o payment-service-openapi.json

# Import into Postman
# File → Import → paste the JSON or drag the file
```

---

## Demo Walkthrough (end-to-end)

The following sequence demonstrates the full happy path. Replace the UUIDs with real account IDs seeded in your database.

```bash
export SENDER="3fa85f64-5717-4562-b3fc-2c963f66afa6"
export RECEIVER="7b3e1d24-98ac-4f2e-b8c1-d9e0f1a23456"
export IDEM_KEY=$(uuidgen)
export USER_ID=$(uuidgen)

# Step 1 — Check sender balance before
curl -s "http://localhost:8080/v1/accounts/$SENDER/balance" -H "X-User-Id: $USER_ID" | jq

# Step 2 — Execute transfer
TRANSFER=$(curl -s -X POST http://localhost:8080/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H "X-User-Id: $USER_ID" \
  -d "{\"senderAccountId\":\"$SENDER\",\"receiverAccountId\":\"$RECEIVER\",\"amount\":\"25.0000\",\"currency\":\"USD\"}")
echo $TRANSFER | jq
TXN_ID=$(echo $TRANSFER | jq -r '.transactionId')

# Step 3 — Retry same transfer (idempotent — no double-charge)
curl -s -X POST http://localhost:8080/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H "X-User-Id: $USER_ID" \
  -d "{\"senderAccountId\":\"$SENDER\",\"receiverAccountId\":\"$RECEIVER\",\"amount\":\"25.0000\",\"currency\":\"USD\"}" | jq

# Step 4 — Check transfer status
curl -s "http://localhost:8080/v1/transfers/$TXN_ID" -H "X-User-Id: $USER_ID" | jq

# Step 5 — Check sender balance after
curl -s "http://localhost:8080/v1/accounts/$SENDER/balance" -H "X-User-Id: $USER_ID" | jq

# Step 6 — View transaction history
curl -s "http://localhost:8080/v1/accounts/$SENDER/transactions" -H "X-User-Id: $USER_ID" | jq

# Step 7 — Admin view all transactions
curl -s "http://localhost:8080/v1/admin/transactions" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Id: $USER_ID" | jq

# Step 8 — Reverse the transfer
curl -s -X POST "http://localhost:8080/v1/transfers/$TXN_ID/reverse" \
  -H "X-User-Id: $USER_ID" | jq

# Step 9 — Verify original transaction is now REVERSED
curl -s "http://localhost:8080/v1/transfers/$TXN_ID" -H "X-User-Id: $USER_ID" | jq
```
