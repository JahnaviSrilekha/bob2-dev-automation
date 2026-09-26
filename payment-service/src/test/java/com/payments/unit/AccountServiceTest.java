package com.payments.unit;

import com.payments.domain.Account;
import com.payments.dto.BalanceResponse;
import com.payments.exception.AccountNotFoundException;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AccountService.
 * ST-003-05, TC-034..TC-036
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private AccountService accountService;

    // ── TC-034: Current balance returns account.balance ────────────────────

    @Test
    void getCurrentBalance_returnsAccountBalance() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, UUID.randomUUID(),
                new BigDecimal("75.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.getCurrentBalance(accountId);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("75.0000"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.asOf()).isNull();
    }

    // ── TC-035: Non-existent account returns 404 ──────────────────────────

    @Test
    void getCurrentBalance_accountNotFound_throws() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getCurrentBalance(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── TC-036: Point-in-time balance uses ledger sum ─────────────────────

    @Test
    void getBalanceAsOf_returnsLedgerSum() {
        UUID accountId = UUID.randomUUID();
        Instant asOf = Instant.parse("2024-01-01T00:00:00Z");
        Account account = new Account(accountId, UUID.randomUUID(),
                new BigDecimal("100.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumNetBalanceAsOf(accountId, asOf))
                .thenReturn(new BigDecimal("50.0000"));

        BalanceResponse response = accountService.getBalanceAsOf(accountId, asOf);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(response.asOf()).isEqualTo(asOf);
    }

    // ── TC-036b: Point-in-time balance with null result defaults to 0 ─────

    @Test
    void getBalanceAsOf_nullLedgerSum_returnsZero() {
        UUID accountId = UUID.randomUUID();
        Instant asOf = Instant.now();
        Account account = new Account(accountId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.sumNetBalanceAsOf(accountId, asOf)).thenReturn(null);

        BalanceResponse response = accountService.getBalanceAsOf(accountId, asOf);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
