package com.payments.integration;

import com.payments.domain.Account;
import com.payments.domain.LedgerEntry;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.InsufficientFundsException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for double-entry ledger correctness.
 * TC-001, TC-011, TC-013, TC-014 — verifies DB state directly.
 * ST-001b-06, ST-008-05
 */
class LedgerIntegrationTest extends AbstractIntegrationTest {

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

    // ── TC-013: Net-zero invariant holds after transfer ────────────────────

    @Test
    void transfer_producesExactlyTwoLedgerEntries_netZero() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("250.0000"), "USD");

        idempotencyService.checkOrCreate(key, request);
        TransferResponse response = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, response);

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(response.transactionId());
        assertThat(entries).hasSize(2);

        // Net-zero: CREDIT amount - DEBIT amount = 0
        BigDecimal net = entries.stream()
                .map(e -> switch (e.getEntryType()) {
                    case CREDIT -> e.getAmount();
                    case DEBIT  -> e.getAmount().negate();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net.compareTo(BigDecimal.ZERO)).isZero();
    }

    // ── TC-011: Entries have correct types, amounts, and account IDs ───────

    @Test
    void transfer_ledgerEntries_haveCorrectFields() {
        String key = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("100.0000");
        TransferRequest request = new TransferRequest(senderId, receiverId, amount, "USD");

        idempotencyService.checkOrCreate(key, request);
        TransferResponse response = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, response);

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(response.transactionId());
        assertThat(entries).hasSize(2);

        LedgerEntry debit  = entries.stream().filter(e -> e.getAccountId().equals(senderId)).findFirst().orElseThrow();
        LedgerEntry credit = entries.stream().filter(e -> e.getAccountId().equals(receiverId)).findFirst().orElseThrow();

        assertThat(debit.getAmount()).isEqualByComparingTo(amount);
        assertThat(credit.getAmount()).isEqualByComparingTo(amount);
        assertThat(debit.getInitiatingUserId()).isNotNull();
        assertThat(credit.getInitiatingUserId()).isNotNull();
        assertThat(debit.getRequestTimestamp()).isNotNull();
    }

    // ── TC-014: Rollback on failure — zero ledger entries ─────────────────

    @Test
    void failedTransfer_producesZeroLedgerEntries() {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("9999.0000"), "USD"); // exceeds balance

        idempotencyService.checkOrCreate(key, request);
        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), key))
                .isInstanceOf(InsufficientFundsException.class);

        // No transaction was created; ledger must be empty for any hypothetical txnId
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    // ── TC-001: Balance updated correctly after transfer ──────────────────

    @Test
    void transfer_updatesBalances_correctly() {
        String key = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("300.0000");
        TransferRequest request = new TransferRequest(senderId, receiverId, amount, "USD");

        idempotencyService.checkOrCreate(key, request);
        transferService.initiateTransfer(request, UUID.randomUUID(), key);

        Account updatedSender = accountRepository.findById(senderId).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverId).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("700.0000"));
        assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("300.0000"));
    }
}
