package com.payments.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payments.domain.IdempotencyKey;
import com.payments.domain.IdempotencyStatus;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.IdempotencyKeyConflictException;
import com.payments.exception.TransferInProgressException;
import com.payments.repository.IdempotencyKeyRepository;
import com.payments.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IdempotencyService.
 * ST-002-06, TC-021..TC-024, TC-026
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService idempotencyService;

    private TransferRequest request;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        idempotencyService = new IdempotencyService(repository, mapper, 24);

        request = new TransferRequest(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("25.0000"), "USD");
        idempotencyKey = UUID.randomUUID().toString();
    }

    // ── TC-021: Completed key returns cached response ─────────────────────

    @Test
    void checkOrCreate_completedKey_returnsCached() throws Exception {
        String hash = idempotencyService.computeHash(request);
        TransferResponse cachedResp = new TransferResponse(UUID.randomUUID(), "COMPLETED",
                new BigDecimal("25.0000"), "USD", Instant.now());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String payload = mapper.writeValueAsString(cachedResp);

        IdempotencyKey existing = new IdempotencyKey(idempotencyKey, hash,
                IdempotencyStatus.COMPLETED, Instant.now(), Instant.now().plusSeconds(86400));
        existing.setResponsePayload(payload);

        when(repository.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(repository.findByKey(idempotencyKey)).thenReturn(Optional.of(existing));

        IdempotencyService.IdempotencyResult result = idempotencyService.checkOrCreate(idempotencyKey, request);

        assertThat(result.isCached()).isTrue();
        assertThat(result.cachedResponse().status()).isEqualTo("COMPLETED");
    }

    // ── TC-022: In-progress key returns HTTP 409 ─────────────────────────

    @Test
    void checkOrCreate_pendingKey_throwsTransferInProgress() {
        String hash = idempotencyService.computeHash(request);
        IdempotencyKey existing = new IdempotencyKey(idempotencyKey, hash,
                IdempotencyStatus.PENDING, Instant.now(), Instant.now().plusSeconds(86400));

        when(repository.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(repository.findByKey(idempotencyKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.checkOrCreate(idempotencyKey, request))
                .isInstanceOf(TransferInProgressException.class);
    }

    // ── TC-023: Different payload with same key returns HTTP 422 ──────────

    @Test
    void checkOrCreate_hashMismatch_throwsConflict() {
        IdempotencyKey existing = new IdempotencyKey(idempotencyKey, "different-hash",
                IdempotencyStatus.COMPLETED, Instant.now(), Instant.now().plusSeconds(86400));

        when(repository.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(repository.findByKey(idempotencyKey)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.checkOrCreate(idempotencyKey, request))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    // ── TC-024: Retry of failed transfer — proceeds ───────────────────────

    @Test
    void checkOrCreate_failedKey_sameHash_proceeds() {
        String hash = idempotencyService.computeHash(request);
        IdempotencyKey existing = new IdempotencyKey(idempotencyKey, hash,
                IdempotencyStatus.FAILED, Instant.now(), Instant.now().plusSeconds(86400));

        when(repository.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(repository.findByKey(idempotencyKey)).thenReturn(Optional.of(existing));

        IdempotencyService.IdempotencyResult result = idempotencyService.checkOrCreate(idempotencyKey, request);

        assertThat(result.isCached()).isFalse();
    }

    // ── TC-020: Hash consistency — same input gives same hash ─────────────

    @Test
    void computeHash_sameInput_sameHash() {
        String hash1 = idempotencyService.computeHash(request);
        String hash2 = idempotencyService.computeHash(request);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex
    }

    // ── First-time key insertion proceeds ─────────────────────────────────

    @Test
    void checkOrCreate_newKey_proceeds() {
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyService.IdempotencyResult result = idempotencyService.checkOrCreate(idempotencyKey, request);

        assertThat(result.isCached()).isFalse();
    }
}
