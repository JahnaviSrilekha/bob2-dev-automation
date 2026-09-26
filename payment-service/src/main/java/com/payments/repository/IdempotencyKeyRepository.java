package com.payments.repository;

import com.payments.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency key data access.
 * The unique constraint on the 'key' column acts as the atomic insertion gate:
 * two concurrent requests with the same key will have exactly one INSERT succeed
 * and the other receive a DataIntegrityViolationException (REQ-F-007, ADR-003).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByKey(String key);

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
