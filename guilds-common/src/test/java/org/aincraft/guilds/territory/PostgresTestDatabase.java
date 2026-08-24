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
        PostgresDatabase database = new PostgresDatabase(settings(url));
        database.initializeSchema();
        return database;
    }

    public static DatabaseSettings settings(String jdbcUrl) {
        return new DatabaseSettings(
                "127.0.0.1", 5432, "azoth_territory",
                envOr("GUILDS_TEST_JDBC_USER", "azoth"),
                envOr("GUILDS_TEST_JDBC_PASSWORD", "azoth"),
                false, 5, jdbcUrl);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
