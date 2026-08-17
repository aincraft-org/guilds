package dev.mintychochip.territory.persist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDialectTest {
    @Test
    void mysqlUsesJsonAndDuplicateKeyUpsert() {
        DatabaseDialect dialect = new MySqlDialect();
        assertTrue(String.join(" ", dialect.schemaStatements()).contains("JSON NOT NULL"));
        assertTrue(dialect.documentUpsertSql("territories", "id").contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(dialect.singletonUpsertSql("invasion_state", "id").contains("VALUES (1, ?)"));
    }

    @Test
    void postgresKeepsJsonbAndConflictUpsert() {
        DatabaseDialect dialect = new PostgresDialect();
        assertTrue(String.join(" ", dialect.schemaStatements()).contains("JSONB NOT NULL"));
        assertTrue(dialect.documentUpsertSql("territories", "id").contains("ON CONFLICT"));
    }
}
