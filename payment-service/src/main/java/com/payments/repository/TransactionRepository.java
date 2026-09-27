package com.payments.repository;

import com.payments.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

/**
 * Transaction data access.
 * JpaSpecificationExecutor added to support dynamic admin filter queries (US-011).
 */
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>,
                JpaSpecificationExecutor<Transaction> {
}
