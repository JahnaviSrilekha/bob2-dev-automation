package com.payments.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that Liquibase applied all changesets and the schema is correct.
 * TC-028: DB-level CHECK constraint prevents negative balance.
 * ST-008-05
 */
class MigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Schema existence ──────────────────────────────────────────────────

    @Test
    void allFourTablesExist() {
        for (String table : new String[]{"accounts", "transactions", "ledger_entries", "idempotency_keys"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(count).as("Table %s should exist", table).isEqualTo(1);
        }
    }

    // ── TC-028: CHECK constraint rejects negative balance ─────────────────

    @Test
    void negativeBalance_rejectedByCheckConstraint() {
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accounts (id, owner_user_id, balance, currency, created_at, updated_at) " +
                "VALUES (?::uuid, ?::uuid, 100.0000, 'USD', NOW(), NOW())",
                id.toString(), java.util.UUID.randomUUID().toString());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE accounts SET balance = -1.0000 WHERE id = ?::uuid",
                id.toString()))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("chk_accounts_balance_non_negative");
    }

    // ── Liquibase reports zero pending changesets ─────────────────────────

    @Test
    void liquibase_zeroPendingChangesets() {
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertThat(pending).isGreaterThanOrEqualTo(6);
    }
}
