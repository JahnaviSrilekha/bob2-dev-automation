package com.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.domain.IdempotencyKey;
import com.payments.domain.IdempotencyStatus;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.IdempotencyKeyConflictException;
import com.payments.exception.TransferInProgressException;
import com.payments.repository.IdempotencyKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Implements the idempotency gate algorithm (§8.2.2, ADR-003).
 *
 * Algorithm:
 *  1. Compute SHA-256 hash of canonical request fields.
 *  2. Attempt atomic INSERT into idempotency_keys with status=PENDING.
 *  3. On unique-constraint violation: read existing row and dispatch:
 *     - COMPLETED + same hash → return cached response (HTTP 200)
 *     - PENDING              → throw TransferInProgressException (HTTP 409)
 *     - any status + hash mismatch → throw IdempotencyKeyConflictException (HTTP 422)
 *     - FAILED + same hash   → allow retry (return PROCEED)
 *
 * ST-002-01 through ST-002-07
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public IdempotencyService(IdempotencyKeyRepository repository,
                              ObjectMapper objectMapper,
                              @Value("${app.idempotency.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    /**
     * Result returned from checkOrCreate().
     * If isCached() == true, cachedResponse() contains the original response.
     * If isCached() == false, the caller should proceed with the transfer.
     */
    public static final class IdempotencyResult {
        private final boolean cached;
        private final TransferResponse cachedResponse;

        private IdempotencyResult(boolean cached, TransferResponse cachedResponse) {
            this.cached = cached;
            this.cachedResponse = cachedResponse;
        }

        public static IdempotencyResult proceed() {
            return new IdempotencyResult(false, null);
        }

        public static IdempotencyResult cached(TransferResponse response) {
            return new IdempotencyResult(true, response);
        }

        public boolean isCached() {
            return cached;
        }

        public TransferResponse cachedResponse() {
            return cachedResponse;
        }
    }

    /**
     * Check idempotency gate before any ledger write.
     *
     * The INSERT attempt runs in REQUIRES_NEW so that a unique-constraint violation
     * does not mark the caller's transaction rollback-only (ADR-003).
     *
     * ST-002-02, ST-002-03
     */
    @Transactional
    public IdempotencyResult checkOrCreate(String key, TransferRequest request) {
        String hash = computeHash(request);

        boolean inserted = tryInsert(key, hash);

        if (inserted) {
            return IdempotencyResult.proceed();
        }

        // Key already exists — read it in the current transaction
        IdempotencyKey existing = repository.findByKey(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key vanished after constraint violation: " + key));

        // Hash mismatch → different payload with same key
        if (!existing.getPayloadHash().equals(hash)) {
            throw new IdempotencyKeyConflictException(key);
        }

        return switch (existing.getStatus()) {
            case PENDING -> throw new TransferInProgressException(key);
            case COMPLETED -> IdempotencyResult.cached(deserializeResponse(existing.getResponsePayload()));
            case FAILED -> IdempotencyResult.proceed(); // allow retry of failed transfer
        };
    }

    /**
     * Called after successful commit to update idempotency key to COMPLETED.
     * ST-002-04
     */
    @Transactional
    public void markCompleted(String key, TransferResponse response) {
        repository.findByKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponsePayload(serializeResponse(response));
            repository.save(record);
        });
    }

    /**
     * Called when the transfer fails to update idempotency key to FAILED.
     */
    @Transactional
    public void markFailed(String key) {
        repository.findByKey(key).ifPresent(record -> {
            if (record.getStatus() == IdempotencyStatus.PENDING) {
                record.setStatus(IdempotencyStatus.FAILED);
                repository.save(record);
            }
        });
    }

    /**
     * Scheduled cleanup of expired idempotency keys (REQ-F-011).
     * Runs every hour; deletes rows where expires_at < NOW().
     * ST-002-05
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    /**
     * SHA-256 of canonical string: senderAccountId|receiverAccountId|amount
     * ST-002-03
     */
    public String computeHash(TransferRequest request) {
        String canonical = request.senderAccountId() + "|"
                + request.receiverAccountId() + "|"
                + request.amount().toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String serializeResponse(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize TransferResponse", ex);
        }
    }

    private TransferResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, TransferResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize cached TransferResponse", ex);
        }
    }
}
