package dev.mintychochip.territory.persist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDialectTest {
    @Test
    void mysqlUsesJsonAndDuplicateKeyUpsert() {
        DatabaseDialect dialect = new MySqlDialect();
        String schema = String.join(" ", dialect.schemaStatements());
        assertTrue(schema.contains("JSON NOT NULL"));
        assertTrue(schema.contains("guild_storage_banks"));
        assertTrue(dialect.documentUpsertSql("territories", "id").contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(dialect.singletonUpsertSql("invasion_state", "id").contains("VALUES (1, ?)"));
    }

    @Test
    void postgresKeepsJsonbAndConflictUpsert() {
        DatabaseDialect dialect = new PostgresDialect();
        String schema = String.join(" ", dialect.schemaStatements());
        assertTrue(schema.contains("JSONB NOT NULL"));
        assertTrue(schema.contains("guild_storage_banks"));
        assertTrue(dialect.documentUpsertSql("territories", "id").contains("ON CONFLICT"));
    }
}
