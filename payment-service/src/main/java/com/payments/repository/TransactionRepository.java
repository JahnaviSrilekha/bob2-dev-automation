package com.payments.repository;

import com.payments.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Transaction data access. Standard CRUD — no custom locking needed here.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
