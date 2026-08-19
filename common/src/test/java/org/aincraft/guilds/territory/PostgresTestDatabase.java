package org.aincraft.guilds.territory;

import org.aincraft.guilds.territory.persist.DatabaseSettings;
import org.aincraft.guilds.territory.persist.PostgresDatabase;

import java.io.IOException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class PostgresTestDatabase {
    private PostgresTestDatabase() {
    }

    public static PostgresDatabase open() throws IOException {
        String url = System.getenv("GUILDS_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        PostgresDatabase database = new PostgresDatabase(new DatabaseSettings(
                "ignored", 5432, "ignored", "ignored", "", false, 5, url));
        database.initializeSchema();
        return database;
    }
}
