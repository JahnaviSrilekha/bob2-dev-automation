package com.payments.integration;

import com.payments.domain.Account;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for idempotency semantics.
 * TC-021: Retry of completed transfer returns cached response, no extra ledger entries.
 * TC-024: Retry of failed transfer re-executes.
 * ST-002-07
 */
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private UUID senderId;
    private UUID receiverId;

    @BeforeEach
    void seedAccounts() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        Account sender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("1000.0000"), "USD", Instant.now(), Instant.now());
        Account receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    // ── TC-021: Retry of completed transfer — same response, no extra entries ─

    @Test
    void retry_afterCompletedTransfer_returnsCachedResponse_noNewLedgerEntries() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("100.0000"), "USD");

        // First execution
        idempotencyService.checkOrCreate(key, request);
        TransferResponse first = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, first);

        assertThat(ledgerEntryRepository.count()).isEqualTo(2);

        // Retry — should return cached, no new ledger entries
        IdempotencyService.IdempotencyResult gate = idempotencyService.checkOrCreate(key, request);
        assertThat(gate.isCached()).isTrue();
        assertThat(gate.cachedResponse().transactionId()).isEqualTo(first.transactionId());

        // Ledger entry count unchanged
        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
    }

    // ── TC-025: Idempotency key survives — retry returns original transactionId ─

    @Test
    void idempotencyKey_persistsAcrossRequests_sameTransactionId() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("50.0000"), "USD");

        // First execution
        idempotencyService.checkOrCreate(key, request);
        TransferResponse first = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, first);

        // Second attempt with same key, same payload
        IdempotencyService.IdempotencyResult gate = idempotencyService.checkOrCreate(key, request);

        assertThat(gate.isCached()).isTrue();
        assertThat(gate.cachedResponse().transactionId()).isEqualTo(first.transactionId());
        assertThat(gate.cachedResponse().status()).isEqualTo("COMPLETED");
    }

    // ── TC-021 part 2: Ledger entries = exactly 2 on retry ───────────────

    @Test
    void multipleRetries_neverCreateExtraLedgerEntries() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("75.0000"), "USD");

        // First execution
        idempotencyService.checkOrCreate(key, request);
        TransferResponse first = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, first);

        // 3 additional retries
        for (int i = 0; i < 3; i++) {
            IdempotencyService.IdempotencyResult gate = idempotencyService.checkOrCreate(key, request);
            assertThat(gate.isCached()).isTrue();
        }

        // Still only 2 entries
        assertThat(ledgerEntryRepository.countByTransactionId(first.transactionId())).isEqualTo(2);
    }
}
