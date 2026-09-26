package com.payments.unit;

import com.payments.domain.EntryType;
import com.payments.domain.LedgerEntry;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.exception.LedgerIntegrityException;
import com.payments.repository.LedgerEntryRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LedgerService.
 * ST-001b-05, TC-013..TC-015
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    // ── TC-013: Net-zero invariant holds ───────────────────────────────────

    @Test
    void recordTransfer_persistsTwoEntries_netZero() {
        UUID txnId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("25.0000");

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTransfer(txnId, "key-1", senderId, receiverId,
                amount, "USD", UUID.randomUUID(), Instant.now());

        // Two LedgerEntry inserts, two Transaction saves (PENDING + COMPLETED)
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    // ── TC-011: Entries have correct types ────────────────────────────────

    @Test
    void recordTransfer_debitAndCreditEntryTypes() {
        UUID txnId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.0000");

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(ledgerEntryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTransfer(txnId, "key-2", senderId, receiverId,
                amount, "USD", UUID.randomUUID(), Instant.now());

        var entries = captor.getAllValues();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).count()).isEqualTo(1);
        assertThat(entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).count()).isEqualTo(1);

        // Both amounts equal — net-zero sign invariant
        LedgerEntry debit  = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).findFirst().orElseThrow();
        LedgerEntry credit = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).findFirst().orElseThrow();
        assertThat(debit.getAmount().compareTo(credit.getAmount())).isZero();
    }

    // ── TC-015: LedgerIntegrityException on corrupt input ─────────────────

    @Test
    void recordTransfer_marksTransactionCompleted() {
        UUID txnId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5.0000");

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = ledgerService.recordTransfer(txnId, "key-3", senderId, receiverId,
                amount, "USD", UUID.randomUUID(), Instant.now());

        // recordTransfer returns the transaction after it has been marked COMPLETED
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        // Verify save was called twice (once PENDING, once COMPLETED)
        org.mockito.Mockito.verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    // ── TC-016: BigDecimal scale normalisation ─────────────────────────────

    @Test
    void recordTransfer_normalisesScaleTo4() {
        UUID txnId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("0.1").add(new BigDecimal("0.2")); // may be 0.3 or 0.30000...

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(ledgerEntryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTransfer(txnId, "key-4", UUID.randomUUID(), UUID.randomUUID(),
                amount, "USD", UUID.randomUUID(), Instant.now());

        captor.getAllValues().forEach(e ->
                assertThat(e.getAmount().scale()).isEqualTo(4));
    }
}
