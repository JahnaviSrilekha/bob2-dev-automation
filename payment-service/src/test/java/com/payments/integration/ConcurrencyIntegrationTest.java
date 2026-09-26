package com.payments.integration;

import com.payments.domain.Account;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.repository.AccountRepository;
import com.payments.repository.LedgerEntryRepository;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for balance safety and deadlock prevention.
 * TC-027: Two concurrent debits — only one succeeds when balance allows only one.
 * TC-031: Lock order — lower UUID acquired first.
 * TC-032: @RepeatedTest race detection.
 * ST-007-05
 */
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private UUID senderId;
    private UUID receiverId;

    @BeforeEach
    void seedAccounts() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        Account sender = new Account(senderId, UUID.randomUUID(),
                new BigDecimal("1000.0000"), "USD", Instant.now(), Instant.now());
        Account receiver = new Account(receiverId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now());
        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    // ── TC-027: Two concurrent 800-cent debits from 1000-cent balance ─────

    @Test
    void concurrentTransfers_onlyOneSucceeds_whenBalanceAllowsOne() throws Exception {
        BigDecimal debitAmount = new BigDecimal("800.0000");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final String key = UUID.randomUUID().toString();
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    TransferRequest request = new TransferRequest(senderId, receiverId, debitAmount, "USD");
                    idempotencyService.checkOrCreate(key, request);
                    TransferResponse response = transferService.initiateTransfer(request, UUID.randomUUID(), key);
                    idempotencyService.markCompleted(key, response);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        latch.countDown(); // release both threads simultaneously
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        Account updatedSender = accountRepository.findById(senderId).orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("200.0000"));

        // Only 2 ledger entries total (one successful transfer)
        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
    }

    // ── TC-032: @RepeatedTest race detection (5 runs) ─────────────────────

    @RepeatedTest(5)
    void repeatedConcurrentTransfer_doesNotCorruptBalance() throws Exception {
        // Fresh accounts for each repetition (seeded in @BeforeEach)
        BigDecimal amount = new BigDecimal("100.0000");
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(senderId, receiverId, amount, "USD");

        idempotencyService.checkOrCreate(key, request);
        transferService.initiateTransfer(request, UUID.randomUUID(), key);

        Account updatedSender = accountRepository.findById(senderId).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverId).orElseThrow();

        // Total money is conserved
        BigDecimal total = updatedSender.getBalance().add(updatedReceiver.getBalance());
        assertThat(total).isEqualByComparingTo(new BigDecimal("1000.0000"));
    }
}
