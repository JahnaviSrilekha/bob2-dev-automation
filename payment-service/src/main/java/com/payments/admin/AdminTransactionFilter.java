package com.payments.admin;

import com.payments.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable filter parameters for the admin transaction view.
 * All fields are optional — null means "no filter applied".
 * REQ-F-030
 */
public record AdminTransactionFilter(
        UUID accountId,
        Instant from,
        Instant to,
        TransactionStatus status
) {
    /** Convenience factory for the no-filter (list-all) case. */
    public static AdminTransactionFilter empty() {
        return new AdminTransactionFilter(null, null, null, null);
    }
}
