package com.payments.service;

import com.payments.domain.Account;
import com.payments.domain.EntryType;
import com.payments.domain.LedgerEntry;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.ReversalResponse;
import com.payments.exception.DeadlockExhaustedException;
import com.payments.exception.InsufficientFundsForReversalException;
import com.payments.exception.TransactionNotFoundException;
import com.payments.exception.TransferAlreadyReversedException;
import com.payments.exception.TransferNotReversibleException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.repository.TransactionRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates transfer reversal: validates state machine, acquires pessimistic locks,
 * reverses balances, writes offsetting ledger entries, publishes event.
 *
 * Lock ordering: account IDs sorted ascending — same deadlock prevention as TransferService
 * (REQ-F-012, ADR-002).
 *
 * REQ-F-025, REQ-F-026, REQ-F-027 / ST-006-02, ST-006-03, ST-006-04
 */
@Service
public class ReversalService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final DomainEventPublisher eventPublisher;

    public ReversalService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           LedgerService ledgerService,
                           DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reverses a COMPLETED transfer within a single @Transactional boundary.
     *
     * Retry policy: up to 3 attempts on CannotAcquireLockException with the same
     * backoff as TransferService (REQ-F-014).
     */
    @Retryable(
        retryFor = CannotAcquireLockException.class,
        noRetryFor = {
            TransferNotReversibleException.class,
            TransferAlreadyReversedException.class,
            InsufficientFundsForReversalException.class,
            TransactionNotFoundException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, random = true)
    )
    @Transactional
    public ReversalResponse reverse(UUID originalTxnId, UUID initiatingUserId) {

        // ST-006-02: Load original transaction
        Transaction original = transactionRepository.findById(originalTxnId)
                .orElseThrow(() -> new TransactionNotFoundException(originalTxnId));

        // ST-006-02: State-machine guards
        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new TransferAlreadyReversedException(originalTxnId);
        }
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new TransferNotReversibleException(originalTxnId, original.getStatus().name());
        }

        // ST-006-03: Acquire pessimistic locks in sorted-ID order (deadlock prevention)
        List<UUID> orderedIds = List.of(original.getSenderAccountId(), original.getReceiverAccountId())
                .stream().sorted().toList();
        List<Account> locked = accountRepository.findByIdForUpdate(orderedIds);

        Account sender = findAccount(locked, original.getSenderAccountId());
        Account receiver = findAccount(locked, original.getReceiverAccountId());

        // ST-006-03: Receiver balance check
        if (receiver.getBalance().compareTo(original.getAmount()) < 0) {
            throw new InsufficientFundsForReversalException(
                    receiver.getId(), receiver.getBalance(), original.getAmount());
        }

        // ST-006-03: Reverse balances (opposite direction to the original transfer)
        sender.setBalance(sender.getBalance().add(original.getAmount()));
        receiver.setBalance(receiver.getBalance().subtract(original.getAmount()));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        // ST-006-03: Fetch original ledger entries to populate reversalOfEntryId
        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(originalTxnId);
        UUID originalDebitEntryId = originalEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Original DEBIT entry missing for transaction " + originalTxnId));
        UUID originalCreditEntryId = originalEntries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Original CREDIT entry missing for transaction " + originalTxnId));

        // ST-006-04: Write reversal ledger entries and reversal transaction row
        UUID reversalTxnId = UUID.randomUUID();
        Instant requestTimestamp = Instant.now();
        ledgerService.recordReversal(reversalTxnId, originalTxnId,
                originalDebitEntryId, originalCreditEntryId,
                original.getSenderAccountId(), original.getReceiverAccountId(),
                original.getAmount(), original.getCurrency(),
                initiatingUserId, requestTimestamp);

        // ST-006-04: Update original transaction status to REVERSED
        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        // Publish event after commit (TransactionSynchronizationManager callback)
        eventPublisher.publishReversedAfterCommit(
                new PaymentReversedEvent(reversalTxnId, originalTxnId,
                        original.getSenderAccountId(), original.getReceiverAccountId(),
                        original.getAmount(), original.getCurrency(),
                        initiatingUserId, requestTimestamp));

        return new ReversalResponse(reversalTxnId, originalTxnId, "REVERSED", requestTimestamp);
    }

    /**
     * Recover from exhausted retry budget — throw DeadlockExhaustedException → HTTP 503.
     */
    @Recover
    public ReversalResponse handleDeadlockExhaustion(CannotAcquireLockException ex,
                                                     UUID originalTxnId,
                                                     UUID initiatingUserId) {
        throw new DeadlockExhaustedException(
                "Reversal failed after 3 lock acquisition attempts", ex);
    }

    /**
     * Catch-all @Recover that re-throws any other RuntimeException to preserve
     * the original domain exception rather than wrapping it in ExhaustedRetryException.
     */
    @Recover
    public ReversalResponse rethrowDomainException(RuntimeException ex,
                                                   UUID originalTxnId,
                                                   UUID initiatingUserId) {
        throw ex;
    }

    private Account findAccount(List<Account> locked, UUID id) {
        return locked.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.payments.exception.AccountNotFoundException(id));
    }
}
