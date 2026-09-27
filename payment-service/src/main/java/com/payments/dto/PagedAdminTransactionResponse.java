package com.payments.dto;

import com.payments.domain.Transaction;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Paginated response envelope for the admin transaction view.
 * REQ-F-028 — ST-011-01
 */
public record PagedAdminTransactionResponse(
        List<TransactionEntry> entries,
        long totalCount,
        int page,
        int pageSize,
        int totalPages
) {

    /**
     * Single transaction summary in the admin view.
     */
    public record TransactionEntry(
            UUID transactionId,
            UUID senderAccountId,
            UUID receiverAccountId,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt
    ) {}

    /** Maps a Spring Data {@link Page} of {@link Transaction} to this DTO. */
    public static PagedAdminTransactionResponse from(Page<Transaction> page) {
        List<TransactionEntry> entries = page.getContent().stream()
                .map(tx -> new TransactionEntry(
                        tx.getId(),
                        tx.getSenderAccountId(),
                        tx.getReceiverAccountId(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getStatus().name(),
                        tx.getCreatedAt()))
                .toList();

        return new PagedAdminTransactionResponse(
                entries,
                page.getTotalElements(),
                page.getNumber() + 1,   // convert 0-based to 1-based
                page.getSize(),
                page.getTotalPages());
    }
}
