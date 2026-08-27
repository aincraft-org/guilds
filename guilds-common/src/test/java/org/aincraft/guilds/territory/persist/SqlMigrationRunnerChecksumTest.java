package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SqlMigrationRunnerChecksumTest {
    /** Scratch track so validation tests never touch the shared {@code persist} rows. */
    private static final String TRACK = "checksum-test";

    private PostgresDatabase database;

    @AfterEach
    void closeDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void rejectsMigrationWhoseAppliedChecksumDiffersFromResource() throws Exception {
        String url = System.getenv("GUILDS_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = new PostgresDatabase(PostgresTestDatabase.settings(url));
        database.initializeSchema();

        SqlMigration migration = SqlMigrationCatalog.load("persist", DatabaseType.POSTGRESQL).getFirst();
        try (Connection connection = database.connection()) {
            recordApplied(connection, TRACK, migration.version(), "deadbeef");
            SQLException failure = assertThrows(SQLException.class,
                    () -> SqlMigrationRunner.validateApplied(connection, TRACK, List.of(migration)));
            assertTrue(failure.getMessage().contains("different checksum"), failure.getMessage());
        }
    }

    @Test
    void acceptsMigrationWithMatchingChecksum() throws Exception {
        String url = System.getenv("GUILDS_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = new PostgresDatabase(PostgresTestDatabase.settings(url));
        database.initializeSchema();

        SqlMigration migration = SqlMigrationCatalog.load("persist", DatabaseType.POSTGRESQL).getFirst();
        try (Connection connection = database.connection()) {
            recordApplied(connection, TRACK, migration.version(), migration.checksum());
            SqlMigrationRunner.validateApplied(connection, TRACK, List.of(migration));
        }
    }

    private static void recordApplied(Connection connection, String track, int version, String checksum)
            throws SQLException {
        String insert = SqlSupport.upsertSql(connection,
                SqlStatements.load("migrations/insert-sql_schema_migrations.sql"),
                "track, version", """
                description = EXCLUDED.description,
                checksum = EXCLUDED.checksum,
                applied_at = EXCLUDED.applied_at
                """);
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, track);
            statement.setInt(2, version);
            statement.setString(3, "test");
            statement.setString(4, checksum);
            statement.setString(5, "2026-08-20T00:00:00");
            statement.executeUpdate();
        }
    }
}
