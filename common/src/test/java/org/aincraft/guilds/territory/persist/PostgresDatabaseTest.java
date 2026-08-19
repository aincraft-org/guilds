package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresDatabaseTest {
    private PostgresDatabase database;

    @AfterEach
    void closeDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void initializesSharedSchemaAgainstConfiguredPostgres() throws Exception {
        String url = System.getenv("GUILDS_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = new PostgresDatabase(new DatabaseSettings(
                "ignored", 5432, "ignored", "ignored", "", false, 5, url));
        database.initializeSchema();
    }
}
