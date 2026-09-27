package com.payments.unit;

import com.payments.admin.AdminTransactionFilter;
import com.payments.admin.AdminTransactionSpecification;
import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AdminTransactionFilter and AdminTransactionSpecification factory.
 * Verifies that the filter record and empty() factory behave correctly,
 * and that withFilter() always returns a non-null Specification regardless of input.
 *
 * Filter correctness against real data is verified in AdminTransactionIntegrationTest.
 * ST-011-04
 */
class AdminTransactionSpecificationTest {

    // ── AdminTransactionFilter.empty() ────────────────────────────────────────

    @Test
    void empty_allFieldsAreNull() {
        AdminTransactionFilter filter = AdminTransactionFilter.empty();
        assertThat(filter.accountId()).isNull();
        assertThat(filter.from()).isNull();
        assertThat(filter.to()).isNull();
        assertThat(filter.status()).isNull();
    }

    // ── withFilter always returns a Specification ─────────────────────────────

    @Test
    void withFilter_empty_returnsNonNullSpecification() {
        Specification<Transaction> spec =
                AdminTransactionSpecification.withFilter(AdminTransactionFilter.empty());
        assertThat(spec).isNotNull();
    }

    @Test
    void withFilter_allFiltersSet_returnsNonNullSpecification() {
        AdminTransactionFilter filter = new AdminTransactionFilter(
                UUID.randomUUID(),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-31T23:59:59Z"),
                TransactionStatus.COMPLETED);

        Specification<Transaction> spec = AdminTransactionSpecification.withFilter(filter);
        assertThat(spec).isNotNull();
    }

    // ── filter record equality ────────────────────────────────────────────────

    @Test
    void filter_equalityByFields() {
        UUID id = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(3600);
        AdminTransactionFilter a = new AdminTransactionFilter(id, from, to, TransactionStatus.FAILED);
        AdminTransactionFilter b = new AdminTransactionFilter(id, from, to, TransactionStatus.FAILED);
        assertThat(a).isEqualTo(b);
    }
}
