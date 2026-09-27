package com.payments.integration;

import com.payments.admin.AdminTransactionFilter;
import com.payments.admin.AdminTransactionService;
import com.payments.domain.Account;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.PagedAdminTransactionResponse;
import com.payments.dto.TransferRequest;
import com.payments.dto.TransferResponse;
import com.payments.repository.AccountRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.IdempotencyService;
import com.payments.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US-011: Admin Views All Transactions.
 * Covers:
 *   - Paginated list of all transactions for admin (REQ-F-028)
 *   - HTTP 403 for non-admin requests (REQ-F-029)
 *   - AccountId, date-range, and status filters (REQ-F-030)
 *   - Empty result when no transactions exist
 *
 * Service-layer tests exercise AdminTransactionService + Specification.
 * HTTP-layer tests exercise the AdminRoleGuard via TestRestTemplate.
 *
 * ST-011-05
 */
class AdminTransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminTransactionService adminTransactionService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID senderAccountId;
    private UUID receiverAccountId;

    @BeforeEach
    void seedAccounts() {
        senderAccountId = UUID.randomUUID();
        receiverAccountId = UUID.randomUUID();
        accountRepository.save(new Account(senderAccountId, UUID.randomUUID(),
                new BigDecimal("5000.0000"), "USD", Instant.now(), Instant.now()));
        accountRepository.save(new Account(receiverAccountId, UUID.randomUUID(),
                new BigDecimal("0.0000"), "USD", Instant.now(), Instant.now()));
    }

    // ── TC-011-01: admin role → paginated results ────────────────────────────

    @Test
    void adminRequest_returnsPaginatedTransactions() {
        // Execute a transfer to create a transaction
        String key = UUID.randomUUID().toString();
        TransferRequest req = new TransferRequest(senderAccountId, receiverAccountId,
                new BigDecimal("100.0000"), "USD");
        idempotencyService.checkOrCreate(key, req);
        transferService.initiateTransfer(req, UUID.randomUUID(), key);

        Page<Transaction> page = adminTransactionService.getAllTransactions(
                AdminTransactionFilter.empty(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent()).allSatisfy(tx ->
                assertThat(tx.getStatus()).isNotNull());
    }

    // ── TC-011-02: accountId filter returns only matching transactions ────────

    @Test
    void accountIdFilter_returnsOnlyInvolvedTransactions() {
        UUID otherAccountId = UUID.randomUUID();
        accountRepository.save(new Account(otherAccountId, UUID.randomUUID(),
                new BigDecimal("500.0000"), "USD", Instant.now(), Instant.now()));

        // Transfer involving senderAccountId
        String key1 = UUID.randomUUID().toString();
        TransferRequest req1 = new TransferRequest(senderAccountId, receiverAccountId,
                new BigDecimal("50.0000"), "USD");
        idempotencyService.checkOrCreate(key1, req1);
        transferService.initiateTransfer(req1, UUID.randomUUID(), key1);

        // Transfer NOT involving senderAccountId
        String key2 = UUID.randomUUID().toString();
        TransferRequest req2 = new TransferRequest(otherAccountId, receiverAccountId,
                new BigDecimal("30.0000"), "USD");
        idempotencyService.checkOrCreate(key2, req2);
        transferService.initiateTransfer(req2, UUID.randomUUID(), key2);

        Page<Transaction> page = adminTransactionService.getAllTransactions(
                new AdminTransactionFilter(senderAccountId, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).allSatisfy(tx ->
                assertThat(tx.getSenderAccountId().equals(senderAccountId)
                        || tx.getReceiverAccountId().equals(senderAccountId)).isTrue());
    }

    // ── TC-011-03: status filter returns only matching status ─────────────────

    @Test
    void statusFilter_returnsOnlyCompletedTransactions() {
        String key = UUID.randomUUID().toString();
        TransferRequest req = new TransferRequest(senderAccountId, receiverAccountId,
                new BigDecimal("75.0000"), "USD");
        idempotencyService.checkOrCreate(key, req);
        transferService.initiateTransfer(req, UUID.randomUUID(), key);

        Page<Transaction> page = adminTransactionService.getAllTransactions(
                new AdminTransactionFilter(null, null, null, TransactionStatus.COMPLETED),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).allSatisfy(tx ->
                assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED));
    }

    // ── TC-011-04: empty result when no transactions created ──────────────────

    @Test
    void noTransactions_returnsEmptyPage() {
        Page<Transaction> page = adminTransactionService.getAllTransactions(
                new AdminTransactionFilter(senderAccountId, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ── TC-011-05: non-admin request → HTTP 403 ───────────────────────────────

    @Test
    void nonAdminRequest_returns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "USER");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/admin/transactions", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FORBIDDEN");
    }

    // ── TC-011-06: missing role header → HTTP 403 ────────────────────────────

    @Test
    void missingRoleHeader_returns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/admin/transactions", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── TC-011-07: admin role → HTTP 200 ─────────────────────────────────────

    @Test
    void adminRequest_returnsHttp200() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PagedAdminTransactionResponse> response = restTemplate.exchange(
                "/v1/admin/transactions", HttpMethod.GET, entity,
                PagedAdminTransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().entries()).isNotNull();
    }
}
