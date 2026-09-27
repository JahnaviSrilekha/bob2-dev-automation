package com.payments.admin;

import com.payments.domain.Transaction;
import com.payments.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the admin transaction view.
 * Read-only; delegates filtering and pagination to TransactionRepository
 * via AdminTransactionSpecification.
 * REQ-F-028, REQ-F-030 — ST-011-03
 */
@Service
@Transactional(readOnly = true)
public class AdminTransactionService {

    private final TransactionRepository transactionRepository;

    public AdminTransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Returns a paginated, optionally filtered page of all transactions.
     * All filter fields in {@code filter} are optional; null means no restriction.
     */
    public Page<Transaction> getAllTransactions(AdminTransactionFilter filter, Pageable pageable) {
        Specification<Transaction> spec = AdminTransactionSpecification.withFilter(filter);
        return transactionRepository.findAll(spec, pageable);
    }
}
