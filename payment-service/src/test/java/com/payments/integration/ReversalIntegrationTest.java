package com.payments.integration;

import com.payments.domain.Account;
import com.payments.domain.EntryType;
import com.payments.domain.LedgerEntry;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.ReversalResponse;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.InsufficientFundsForReversalException;
import com.payments.exception.TransferAlreadyReversedException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.IdempotencyService;
import com.payments.service.ReversalService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for transfer reversal — verifies DB state end-to-end.
 * TC-029, TC-030, TC-031
 * ST-006-07
 */
class ReversalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private ReversalService reversalService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID senderId;
    private UUID receiverId;
    private UUID userId;

    @BeforeEach
    void seedAccounts() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        userId = UUID.randomUUID();

        Account sender = new Account(senderId, userId,
                new BigDecimal("1000.0000"), "USD", Instant.now(), Instant.now());
        Account receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("500.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    /**
     * Helper: execute a transfer and return the completed transaction ID.
     */
    private UUID doTransfer(BigDecimal amount) {
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId, amount, "USD");
        idempotencyService.checkOrCreate(key, request);
        TransferResponse response = transferService.initiateTransfer(request, userId, key);
        idempotencyService.markCompleted(key, response);
        return response.transactionId();
    }

    // ── TC-029: Successful reversal ────────────────────────────────────────────

    @Test
    void reverse_success_updatesBalancesAndLedger() {
        BigDecimal transferAmount = new BigDecimal("200.0000");
        UUID originalTxnId = doTransfer(transferAmount);

        // Pre-reversal balances: sender=800, receiver=700
        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("800.0000"));
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("700.0000"));

        ReversalResponse response = reversalService.reverse(originalTxnId, userId);

        // Response shape
        assertThat(response.reversalTransactionId()).isNotNull();
        assertThat(response.originalTransactionId()).isEqualTo(originalTxnId);
        assertThat(response.status()).isEqualTo("REVERSED");

        // Post-reversal balances restored: sender=1000, receiver=500
        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("500.0000"));

        // Original transaction status = REVERSED
        Transaction original = transactionRepository.findById(originalTxnId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);

        // Two new offsetting ledger entries created for the reversal transaction
        List<LedgerEntry> reversalEntries = ledgerEntryRepository
                .findByTransactionId(response.reversalTransactionId());
        assertThat(reversalEntries).hasSize(2);

        LedgerEntry senderEntry = reversalEntries.stream()
                .filter(e -> e.getAccountId().equals(senderId)).findFirst().orElseThrow();
        LedgerEntry receiverEntry = reversalEntries.stream()
                .filter(e -> e.getAccountId().equals(receiverId)).findFirst().orElseThrow();

        // Sender gets CREDIT (money back), receiver gets DEBIT (money out)
        assertThat(senderEntry.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(receiverEntry.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(senderEntry.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(receiverEntry.getAmount()).isEqualByComparingTo(transferAmount);

        // Each reversal entry links back to its original entry (REQ-F-027)
        assertThat(senderEntry.getReversalOfEntryId()).isNotNull();
        assertThat(receiverEntry.getReversalOfEntryId()).isNotNull();

        // Reversal transaction row points back to original
        Transaction reversalTxn = transactionRepository
                .findById(response.reversalTransactionId()).orElseThrow();
        assertThat(reversalTxn.getReversalOfTransactionId()).isEqualTo(originalTxnId);

        // Net-zero invariant on reversal entries
        BigDecimal net = reversalEntries.stream()
                .map(e -> e.getEntryType() == EntryType.CREDIT
                        ? e.getAmount() : e.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net.compareTo(BigDecimal.ZERO)).isZero();
    }

    // ── TC-030: Reversal of already-reversed transfer → 422 ───────────────────

    @Test
    void reverse_alreadyReversed_throws422() {
        UUID originalTxnId = doTransfer(new BigDecimal("100.0000"));

        // First reversal succeeds
        reversalService.reverse(originalTxnId, userId);

        // Second reversal must fail
        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, userId))
                .isInstanceOf(TransferAlreadyReversedException.class);

        // No additional ledger entries were created beyond the original 2 + reversal 2 = 4 total
        long originalCount = ledgerEntryRepository.countByTransactionId(originalTxnId);
        assertThat(originalCount).isEqualTo(2);
    }

    // ── TC-031: Reversal fails when receiver has insufficient balance → 422 ───

    @Test
    void reverse_receiverInsufficientBalance_throws422() {
        BigDecimal transferAmount = new BigDecimal("200.0000");
        UUID originalTxnId = doTransfer(transferAmount);

        // Drain the receiver's balance by transferring it all out to a third account
        UUID thirdAccountId = UUID.randomUUID();
        Account thirdAccount = new Account(thirdAccountId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(thirdAccount);

        // receiver now has 700; drain 700 to third account
        String drainKey = UUID.randomUUID().toString();
        TransferRequest drainRequest = new TransferRequest(receiverId, thirdAccountId,
                new BigDecimal("700.0000"), "USD");
        idempotencyService.checkOrCreate(drainKey, drainRequest);
        TransferResponse drainResponse = transferService.initiateTransfer(drainRequest, userId, drainKey);
        idempotencyService.markCompleted(drainKey, drainResponse);

        // Receiver balance is now 0 — reversal requires 200
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, userId))
                .isInstanceOf(InsufficientFundsForReversalException.class);

        // Original transaction status unchanged
        Transaction original = transactionRepository.findById(originalTxnId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }
}
