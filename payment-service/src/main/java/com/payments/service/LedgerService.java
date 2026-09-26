package com.payments.service;

import com.payments.domain.EntryType;
import com.payments.domain.LedgerEntry;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.exception.LedgerIntegrityException;
import com.payments.repository.LedgerEntryRepository;
import com.payments.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes exactly two ledger entries (DEBIT + CREDIT) and the Transaction row
 * inside the caller's existing @Transactional boundary (Propagation.MANDATORY).
 *
 * Net-zero invariant: debit.amount == credit.amount (both positive, representing
 * the absolute transfer value). Signed sum is 0 when DEBIT is treated as negative.
 *
 * REQ-F-001, REQ-F-015, REQ-F-017 / ST-001b-03
 */
@Service
public class LedgerService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(TransactionRepository transactionRepository,
                         LedgerEntryRepository ledgerEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Persists a Transaction + two LedgerEntry rows within the caller's transaction.
     *
     * @param txnId             system-generated transaction ID
     * @param idempotencyKey    client idempotency key (stored for audit)
     * @param senderAccountId   account to DEBIT
     * @param receiverAccountId account to CREDIT
     * @param amount            transfer amount, scale=4, HALF_EVEN (always positive)
     * @param currency          ISO-4217 currency code
     * @param initiatingUserId  X-User-Id from API Gateway
     * @param requestTimestamp  time the transfer request was received
     * @return the persisted Transaction entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction recordTransfer(UUID txnId, String idempotencyKey,
                                      UUID senderAccountId, UUID receiverAccountId,
                                      BigDecimal amount, String currency,
                                      UUID initiatingUserId, Instant requestTimestamp) {

        BigDecimal scaled = amount.setScale(4, RoundingMode.HALF_EVEN);

        // a. Persist Transaction in PENDING state
        Transaction txn = new Transaction(txnId, idempotencyKey, senderAccountId,
                receiverAccountId, scaled, currency, TransactionStatus.PENDING);
        transactionRepository.save(txn);

        // b. Persist DEBIT entry (sender)
        LedgerEntry debit = new LedgerEntry(
                UUID.randomUUID(), txnId, senderAccountId,
                EntryType.DEBIT, scaled, currency,
                initiatingUserId, requestTimestamp, null);
        ledgerEntryRepository.save(debit);

        // c. Persist CREDIT entry (receiver)
        LedgerEntry credit = new LedgerEntry(
                UUID.randomUUID(), txnId, receiverAccountId,
                EntryType.CREDIT, scaled, currency,
                initiatingUserId, requestTimestamp, null);
        ledgerEntryRepository.save(credit);

        // d. Net-zero assertion: both amounts are the same positive value (REQ-F-017)
        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new LedgerIntegrityException(
                    "Net-zero invariant violated: debit=" + debit.getAmount().toPlainString()
                    + " credit=" + credit.getAmount().toPlainString()
                    + " txnId=" + txnId);
        }

        // e. Mark COMPLETED
        txn.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(txn);

        return txn;
    }
}
