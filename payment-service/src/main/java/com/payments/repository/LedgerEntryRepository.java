package com.payments.repository;

import com.payments.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ledger entry data access — INSERT only, no UPDATE/DELETE (REQ-F-016).
 * The @Immutable on the entity and DB-level permission revocation enforce immutability.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByAccountIdOrderByRequestTimestampDesc(UUID accountId, Pageable pageable);

    /**
     * Point-in-time balance: sum CREDIT entries minus sum DEBIT entries up to asOf.
     * Used by AccountService.getBalanceAsOf() (REQ-F-019).
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
        FROM LedgerEntry e
        WHERE e.accountId = :accountId
          AND e.requestTimestamp <= :asOf
        """)
    BigDecimal sumNetBalanceAsOf(@Param("accountId") UUID accountId, @Param("asOf") Instant asOf);

    /**
     * Count entries for a transaction — used by net-zero integrity checks in tests.
     */
    long countByTransactionId(UUID transactionId);
}
