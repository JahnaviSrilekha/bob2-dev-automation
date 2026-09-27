package com.payments.unit;

import com.payments.admin.AdminTransactionFilter;
import com.payments.admin.AdminTransactionService;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminTransactionService.
 * Verifies that the service delegates to the repository with the correct
 * Specification and Pageable, and passes through the Page result.
 * ST-011-04
 */
@ExtendWith(MockitoExtension.class)
class AdminTransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AdminTransactionService adminTransactionService;

    // ── delegates to repository and returns page ──────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAllTransactions_delegatesToRepositoryAndReturnsPage() {
        Page<Transaction> expectedPage = new PageImpl<>(List.of());
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(expectedPage);

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> result = adminTransactionService.getAllTransactions(
                AdminTransactionFilter.empty(), pageable);

        assertThat(result).isSameAs(expectedPage);
        verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── passes non-empty filter through to repository ─────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAllTransactions_withFilter_passesThroughToRepository() {
        Page<Transaction> expectedPage = new PageImpl<>(List.of());
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(expectedPage);

        AdminTransactionFilter filter = new AdminTransactionFilter(
                UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now(),
                TransactionStatus.COMPLETED);
        Pageable pageable = PageRequest.of(0, 10);

        adminTransactionService.getAllTransactions(filter, pageable);

        verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── pagination params are honoured ───────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAllTransactions_honoursPaginationParams() {
        Pageable pageable = PageRequest.of(2, 50);
        Page<Transaction> expectedPage = new PageImpl<>(List.of(), pageable, 0);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(expectedPage);

        Page<Transaction> result = adminTransactionService.getAllTransactions(
                AdminTransactionFilter.empty(), pageable);

        assertThat(result.getPageable()).isEqualTo(pageable);
    }
}
