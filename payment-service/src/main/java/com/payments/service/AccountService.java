package com.payments.service;

import com.payments.dto.BalanceResponse;
import com.payments.exception.AccountNotFoundException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Balance inquiry service.
 * getCurrentBalance reads the authoritative balance column.
 * getBalanceAsOf computes a point-in-time balance from ledger_entries (REQ-F-018, REQ-F-019).
 * ST-003-02, ST-003-03
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Returns current balance from the accounts table.
     * ST-003-02
     */
    @Transactional(readOnly = true)
    public BalanceResponse getCurrentBalance(UUID accountId) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return new BalanceResponse(account.getId(), account.getBalance(),
                account.getCurrency(), null);
    }

    /**
     * Computes point-in-time balance: SUM(CREDIT) - SUM(DEBIT) from ledger_entries
     * where account_id = ? AND request_timestamp <= asOf.
     * Returns 0.0000 if no entries exist before asOf.
     * ST-003-03
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalanceAsOf(UUID accountId, Instant asOf) {
        // Verify account exists first
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal netBalance = ledgerEntryRepository.sumNetBalanceAsOf(accountId, asOf);
        if (netBalance == null) {
            netBalance = BigDecimal.ZERO;
        }
        BigDecimal scaled = netBalance.setScale(4, RoundingMode.HALF_EVEN);
        return new BalanceResponse(accountId, scaled, account.getCurrency(), asOf);
    }
}
