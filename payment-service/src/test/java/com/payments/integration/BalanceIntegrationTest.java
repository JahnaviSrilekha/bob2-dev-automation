package com.payments.integration;

import com.payments.domain.Account;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.repository.AccountRepository;
import com.payments.service.AccountService;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for balance inquiry endpoint.
 * TC-034..TC-036
 * ST-003-06
 */
class BalanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyService idempotencyService;

    private UUID accountId;

    @BeforeEach
    void seedAccount() {
        accountId = UUID.randomUUID();
        Account account = new Account(accountId, UUID.randomUUID(),
                new BigDecimal("750.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(account);
    }

    // ── TC-034: Current balance returns correct value ─────────────────────

    @Test
    void getCurrentBalance_returnsCorrectBalance() {
        var response = accountService.getCurrentBalance(accountId);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("750.0000"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.asOf()).isNull();
    }

    // ── TC-035: Non-existent account returns 404 ──────────────────────────

    @Test
    void getCurrentBalance_nonExistentAccount_throws() {
        var nonExistentId = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> accountService.getCurrentBalance(nonExistentId))
                .isInstanceOf(com.payments.exception.AccountNotFoundException.class);
    }

    // ── TC-036: Point-in-time balance ─────────────────────────────────────

    @Test
    void getBalanceAsOf_returnsNetLedgerSum() {
        UUID receiverId = UUID.randomUUID();
        Account receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(receiver);

        // Perform a transfer
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(accountId, receiverId,
                new BigDecimal("200.0000"), "USD");
        idempotencyService.checkOrCreate(key, request);
        TransferResponse resp = transferService.initiateTransfer(request, UUID.randomUUID(), key);
        idempotencyService.markCompleted(key, resp);

        // Point-in-time balance AFTER the transfer
        Instant afterTransfer = Instant.now().plusSeconds(1);
        var balanceResponse = accountService.getBalanceAsOf(accountId, afterTransfer);

        // Net sum from ledger: -200 for sender
        assertThat(balanceResponse.balance()).isEqualByComparingTo(new BigDecimal("-200.0000"));
    }
}
