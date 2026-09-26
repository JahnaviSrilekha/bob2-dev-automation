package com.payments.unit;

import com.payments.domain.Account;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.AccountNotFoundException;
import com.payments.exception.DeadlockExhaustedException;
import com.payments.exception.InsufficientFundsException;
import com.payments.exception.InvalidAmountException;
import com.payments.exception.SelfTransferException;
import com.payments.repository.AccountRepository;
import com.payments.service.DomainEventPublisher;
import com.payments.service.LedgerService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransferService covering all rejection paths.
 * ST-001a-05, TC-002..TC-010
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private TransferService transferService;

    private UUID senderId;
    private UUID receiverId;
    private Account sender;
    private Account receiver;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        // Ensure consistent sort order (sender ID < receiver ID) by using predictable UUIDs
        if (senderId.compareTo(receiverId) > 0) {
            UUID tmp = senderId;
            senderId = receiverId;
            receiverId = tmp;
        }
        sender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("100.0000"), "USD", Instant.now(), Instant.now());
        receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("50.0000"), "USD", Instant.now(), Instant.now());
    }

    // ── TC-001: Successful transfer ─────────────────────────────────────────

    @Test
    void initiateTransfer_success() {
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, receiver));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        Transaction txn = new Transaction(UUID.randomUUID(), "key-1", senderId, receiverId,
                new BigDecimal("25.0000"), "USD", TransactionStatus.COMPLETED);
        when(ledgerService.recordTransfer(any(), anyString(), any(), any(),
                any(), anyString(), any(), any())).thenReturn(txn);

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("25.0000"), "USD");
        TransferResponse response = transferService.initiateTransfer(request, UUID.randomUUID(), "key-1");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    // ── TC-002: Insufficient funds ──────────────────────────────────────────

    @Test
    void initiateTransfer_insufficientFunds_throws() {
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, receiver));

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("200.0000"), "USD"); // more than balance of 100

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-2"))
                .isInstanceOf(InsufficientFundsException.class);

        verify(ledgerService, never()).recordTransfer(any(), anyString(), any(), any(),
                any(), anyString(), any(), any());
    }

    // ── TC-003: Zero amount ─────────────────────────────────────────────────

    @Test
    void initiateTransfer_zeroAmount_throws() {
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, receiver));

        TransferRequest request = new TransferRequest(senderId, receiverId,
                BigDecimal.ZERO, "USD");

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-3"))
                .isInstanceOf(InvalidAmountException.class);
    }

    // ── TC-004: Negative amount (pre-validation via @Positive but also tested here) ─

    @Test
    void initiateTransfer_negativeAmount_throws() {
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sender, receiver));

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("-1.0000"), "USD");

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-4"))
                .isInstanceOf(InvalidAmountException.class);
    }

    // ── TC-005: Self-transfer ───────────────────────────────────────────────

    @Test
    void initiateTransfer_selfTransfer_throws() {
        Account sameAccount = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("100.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(sameAccount, sameAccount));

        TransferRequest request = new TransferRequest(senderId, senderId,
                new BigDecimal("10.0000"), "USD");

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-5"))
                .isInstanceOf(SelfTransferException.class);
    }

    // ── TC-010: Sender account does not exist ───────────────────────────────

    @Test
    void initiateTransfer_senderAccountNotFound_throws() {
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of()); // no accounts

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("10.0000"), "USD");

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-6"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── TC-007: Exact balance boundary — succeeds ───────────────────────────

    @Test
    void initiateTransfer_exactBalance_succeeds() {
        Account exactSender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("25.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(exactSender, receiver));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        Transaction txn = new Transaction(UUID.randomUUID(), "key-7", senderId, receiverId,
                new BigDecimal("25.0000"), "USD", TransactionStatus.COMPLETED);
        when(ledgerService.recordTransfer(any(), anyString(), any(), any(),
                any(), anyString(), any(), any())).thenReturn(txn);

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("25.0000"), "USD");
        TransferResponse response = transferService.initiateTransfer(request, UUID.randomUUID(), "key-7");

        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    // ── TC-008: Balance + 1 cent — fails ────────────────────────────────────

    @Test
    void initiateTransfer_balancePlusOneCent_fails() {
        Account exactSender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("25.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findByIdForUpdate(anyList())).thenReturn(List.of(exactSender, receiver));

        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("25.0001"), "USD");

        assertThatThrownBy(() -> transferService.initiateTransfer(request, UUID.randomUUID(), "key-8"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    // ── TC-030: Deadlock recover method ─────────────────────────────────────

    @Test
    void handleDeadlockExhaustion_throwsDeadlockExhaustedException() {
        TransferRequest request = new TransferRequest(senderId, receiverId,
                new BigDecimal("10.0000"), "USD");

        assertThatThrownBy(() -> transferService.handleDeadlockExhaustion(
                new CannotAcquireLockException("lock timeout"),
                request, UUID.randomUUID(), "key-30"))
                .isInstanceOf(DeadlockExhaustedException.class);
    }
}
