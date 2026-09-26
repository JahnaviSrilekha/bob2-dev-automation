package com.payments.service;

import com.payments.domain.Account;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.exception.AccountNotFoundException;
import com.payments.exception.DeadlockExhaustedException;
import com.payments.exception.InsufficientFundsException;
import com.payments.exception.InvalidAmountException;
import com.payments.exception.SelfTransferException;
import com.payments.repository.AccountRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a transfer: acquire pessimistic locks, validate, mutate balances,
 * write ledger, publish event.
 *
 * @Retryable on CannotAcquireLockException with up to 3 attempts + exponential backoff
 * prevents deadlocks from surfacing to the caller (REQ-F-014, ADR-002).
 *
 * Lock ordering: account IDs sorted ascending before acquiring locks — prevents
 * circular waits between two concurrent transfers (REQ-F-012).
 *
 * ST-001a-03, ST-001b-04, ST-007-02, ST-007-03
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final DomainEventPublisher eventPublisher;

    public TransferService(AccountRepository accountRepository,
                           LedgerService ledgerService,
                           DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initiates a transfer within a single @Transactional boundary.
     *
     * Retry policy: up to 3 attempts on CannotAcquireLockException
     * with backoff starting at 50 ms, multiplier 2.0, plus random jitter (REQ-F-014).
     */
    @Retryable(
        retryFor = CannotAcquireLockException.class,
        noRetryFor = {
            InsufficientFundsException.class,
            InvalidAmountException.class,
            SelfTransferException.class,
            AccountNotFoundException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, random = true)
    )
    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request,
                                             UUID initiatingUserId,
                                             String idempotencyKey) {

        // 1. Sort IDs ascending to acquire locks in a consistent order (deadlock prevention)
        List<UUID> orderedIds = List.of(request.senderAccountId(), request.receiverAccountId())
                .stream().sorted().toList();

        // 2. Acquire pessimistic row locks (SELECT … FOR UPDATE) in ascending ID order
        List<Account> locked = accountRepository.findByIdForUpdate(orderedIds);
        Account sender = findAccount(locked, request.senderAccountId());
        Account receiver = findAccount(locked, request.receiverAccountId());

        // 3. Validate business rules
        BigDecimal scaled = request.amount().setScale(4, RoundingMode.HALF_EVEN);

        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(scaled);
        }
        if (request.senderAccountId().equals(request.receiverAccountId())) {
            throw new SelfTransferException();
        }
        if (sender.getBalance().compareTo(scaled) < 0) {
            throw new InsufficientFundsException(sender.getBalance(), scaled);
        }

        // 4. Mutate balances (all BigDecimal, scale=4, HALF_EVEN — CON-003)
        sender.setBalance(sender.getBalance().subtract(scaled));
        receiver.setBalance(receiver.getBalance().add(scaled));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        // 5. Write ledger (within same @Transactional boundary — Propagation.MANDATORY)
        UUID txnId = UUID.randomUUID();
        Instant requestTimestamp = Instant.now();
        ledgerService.recordTransfer(txnId, idempotencyKey,
                sender.getId(), receiver.getId(),
                scaled, request.currency(),
                initiatingUserId, requestTimestamp);

        TransferResponse response = new TransferResponse(
                txnId, "COMPLETED", scaled, request.currency(), requestTimestamp);

        // 6. Publish event after commit (TransactionSynchronizationManager callback)
        eventPublisher.publishAfterCommit(
                new PaymentCompletedEvent(txnId, sender.getId(), receiver.getId(),
                        scaled, request.currency(), initiatingUserId, requestTimestamp));

        return response;
    }

    /**
     * Recover from exhausted retry budget — throw DeadlockExhaustedException → HTTP 503.
     * Only called when CannotAcquireLockException exhausts all 3 attempts.
     * ST-007-04
     */
    @Recover
    public TransferResponse handleDeadlockExhaustion(CannotAcquireLockException ex,
                                                     TransferRequest request,
                                                     UUID initiatingUserId,
                                                     String idempotencyKey) {
        throw new DeadlockExhaustedException(
                "Transfer failed after 3 lock acquisition attempts", ex);
    }

    /**
     * Catch-all @Recover that re-throws any other RuntimeException that Spring Retry
     * routes here when no more specific recovery method matches.
     * This preserves the original domain exception (InsufficientFundsException, etc.)
     * rather than wrapping it in ExhaustedRetryException.
     */
    @Recover
    public TransferResponse rethrowDomainException(RuntimeException ex,
                                                   TransferRequest request,
                                                   UUID initiatingUserId,
                                                   String idempotencyKey) {
        throw ex;
    }

    private Account findAccount(List<Account> locked, UUID id) {
        return locked.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
