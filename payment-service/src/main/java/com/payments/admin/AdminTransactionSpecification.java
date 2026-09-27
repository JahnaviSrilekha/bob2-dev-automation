package com.payments.admin;

import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic JPA Specification for the admin transaction view.
 * Each filter component is optional — absent filters are simply omitted from
 * the WHERE clause. All present predicates are combined with AND.
 *
 * accountId matches transactions where the given ID is either the sender
 * or the receiver (OR predicate per REQ-F-030).
 *
 * ST-011-02
 */
public final class AdminTransactionSpecification {

    private AdminTransactionSpecification() {}

    /**
     * Builds a Specification from the supplied filter.
     * Returns a no-restriction spec when all filter fields are null.
     */
    public static Specification<Transaction> withFilter(AdminTransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.accountId() != null) {
                Predicate matchesSender = cb.equal(root.get("senderAccountId"), filter.accountId());
                Predicate matchesReceiver = cb.equal(root.get("receiverAccountId"), filter.accountId());
                predicates.add(cb.or(matchesSender, matchesReceiver));
            }

            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }

            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
