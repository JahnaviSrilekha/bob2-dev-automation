package com.payments.unit;

import com.payments.domain.Account;
import com.payments.domain.EntryType;
import com.payments.domain.LedgerEntry;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.ReversalResponse;
import com.payments.exception.InsufficientFundsForReversalException;
import com.payments.exception.TransactionNotFoundException;
import com.payments.exception.TransferAlreadyReversedException;
import com.payments.exception.TransferNotReversibleException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.DomainEventPublisher;
import com.payments.service.LedgerService;
import com.payments.service.PaymentReversedEvent;
import com.payments.service.ReversalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReversalService covering all guard paths and the happy path.
 * ST-006-06, TC-023..TC-027
 */
@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private ReversalService reversalService;

    private UUID senderId;
    private UUID receiverId;
    private UUID originalTxnId;
    private Account sender;
    private Account receiver;
    private Transaction completedTxn;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        // Ensure consistent sort order for lock acquisition
        if (senderId.compareTo(receiverId) > 0) {
            UUID tmp = senderId;
            senderId = receiverId;
            receiverId = tmp;
        }
        originalTxnId = UUID.randomUUID();

        sender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("50.0000"), "USD", Instant.now(), Instant.now());
        receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("100.0000"), "USD", Instant.now(), Instant.now());

        completedTxn = new Transaction(originalTxnId, "key-orig", senderId, receiverId,
                new BigDecimal("30.0000"), "USD", TransactionStatus.COMPLETED);
    }

    // ── TC-023: Transaction not found ─────────────────────────────────────────

    @Test
    void reverse_transactionNotFound_throws() {
        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, UUID.randomUUID()))
                .isInstanceOf(TransactionNotFoundException.class);

        verify(accountRepository, never()).findByIdForUpdate(anyList());
        verify(ledgerService, never()).recordReversal(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    // ── TC-024: Already reversed guard ────────────────────────────────────────

    @Test
    void reverse_alreadyReversed_throws() {
        Transaction reversedTxn = new Transaction(originalTxnId, "key-orig", senderId, receiverId,
                new BigDecimal("30.0000"), "USD", TransactionStatus.REVERSED);
        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.of(reversedTxn));

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, UUID.randomUUID()))
                .isInstanceOf(TransferAlreadyReversedException.class);

        verify(accountRepository, never()).findByIdForUpdate(anyList());
    }

    // ── TC-025: FAILED status guard ───────────────────────────────────────────

    @Test
    void reverse_failedTransaction_throws() {
        Transaction failedTxn = new Transaction(originalTxnId, "key-orig", senderId, receiverId,
                new BigDecimal("30.0000"), "USD", TransactionStatus.FAILED);
        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.of(failedTxn));

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, UUID.randomUUID()))
                .isInstanceOf(TransferNotReversibleException.class)
                .hasMessageContaining("FAILED");

        verify(accountRepository, never()).findByIdForUpdate(anyList());
    }

    // ── TC-026: PENDING status guard ──────────────────────────────────────────

    @Test
    void reverse_pendingTransaction_throws() {
        Transaction pendingTxn = new Transaction(originalTxnId, "key-orig", senderId, receiverId,
                new BigDecimal("30.0000"), "USD", TransactionStatus.PENDING);
        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.of(pendingTxn));

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, UUID.randomUUID()))
                .isInstanceOf(TransferNotReversibleException.class)
                .hasMessageContaining("PENDING");

        verify(accountRepository, never()).findByIdForUpdate(anyList());
    }

    // ── TC-027: Receiver insufficient balance ─────────────────────────────────

    @Test
    void reverse_receiverInsufficientBalance_throws() {
        Account poorReceiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("10.0000"), "USD", Instant.now(), Instant.now()); // less than 30
        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.of(completedTxn));
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, poorReceiver));

        assertThatThrownBy(() -> reversalService.reverse(originalTxnId, UUID.randomUUID()))
                .isInstanceOf(InsufficientFundsForReversalException.class);

        verify(ledgerService, never()).recordReversal(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    // ── TC-028: Successful reversal ───────────────────────────────────────────

    @Test
    void reverse_success_mutatesBalancesAndWritesLedger() {
        UUID userId = UUID.randomUUID();
        UUID debitEntryId = UUID.randomUUID();
        UUID creditEntryId = UUID.randomUUID();

        LedgerEntry originalDebit = new LedgerEntry(debitEntryId, originalTxnId, senderId,
                EntryType.DEBIT, new BigDecimal("30.0000"), "USD", userId, Instant.now(), null);
        LedgerEntry originalCredit = new LedgerEntry(creditEntryId, originalTxnId, receiverId,
                EntryType.CREDIT, new BigDecimal("30.0000"), "USD", userId, Instant.now(), null);

        Transaction reversalTxn = new Transaction(UUID.randomUUID(), null, senderId, receiverId,
                new BigDecimal("30.0000"), "USD", TransactionStatus.COMPLETED);

        when(transactionRepository.findById(originalTxnId)).thenReturn(Optional.of(completedTxn));
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, receiver));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.findByTransactionId(originalTxnId))
                .thenReturn(List.of(originalDebit, originalCredit));
        when(ledgerService.recordReversal(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(reversalTxn);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReversalResponse response = reversalService.reverse(originalTxnId, userId);

        // Response fields
        assertThat(response.originalTransactionId()).isEqualTo(originalTxnId);
        assertThat(response.status()).isEqualTo("REVERSED");
        assertThat(response.reversalTransactionId()).isNotNull();

        // Both accounts saved — sender +30, receiver -30
        ArgumentCaptor<Account> savedAccounts = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, org.mockito.Mockito.times(2)).save(savedAccounts.capture());
        BigDecimal senderBalance = savedAccounts.getAllValues().stream()
                .filter(a -> a.getId().equals(senderId))
                .findFirst().orElseThrow().getBalance();
        BigDecimal receiverBalance = savedAccounts.getAllValues().stream()
                .filter(a -> a.getId().equals(receiverId))
                .findFirst().orElseThrow().getBalance();
        assertThat(senderBalance).isEqualByComparingTo(new BigDecimal("80.0000"));  // 50 + 30
        assertThat(receiverBalance).isEqualByComparingTo(new BigDecimal("70.0000")); // 100 - 30

        // Original transaction updated to REVERSED
        ArgumentCaptor<Transaction> savedTxn = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.atLeastOnce()).save(savedTxn.capture());
        boolean reversedStatusSaved = savedTxn.getAllValues().stream()
                .anyMatch(t -> t.getId().equals(originalTxnId)
                        && t.getStatus() == TransactionStatus.REVERSED);
        assertThat(reversedStatusSaved).isTrue();

        // Event published
        ArgumentCaptor<PaymentReversedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentReversedEvent.class);
        verify(eventPublisher).publishReversedAfterCommit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().originalTransactionId()).isEqualTo(originalTxnId);
        assertThat(eventCaptor.getValue().initiatingUserId()).isEqualTo(userId);
    }
}
