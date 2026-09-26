package com.payments.repository;

import com.payments.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Account data access.
 * findByIdForUpdate acquires PESSIMISTIC_WRITE locks via FOR UPDATE in the SQL.
 * @Lock is intentionally omitted: Hibernate 6 rejects lock-mode on native queries;
 * the FOR UPDATE clause in the SQL already serialises row access (REQ-F-012, ADR-002).
 * IDs are pre-sorted ascending by the caller (TransferService) before passing here.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query(value = "SELECT * FROM accounts WHERE id IN (:ids) ORDER BY id FOR UPDATE",
           nativeQuery = true)
    List<Account> findByIdForUpdate(@Param("ids") List<UUID> ids);
}
