package com.azoth.territory;

import com.azoth.territory.persist.DatabaseSettings;
import com.azoth.territory.persist.PostgresDatabase;

import java.io.IOException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class PostgresTestDatabase {
    private PostgresTestDatabase() {
    }

    public static PostgresDatabase open() throws IOException {
        String url = System.getenv("AZOTH_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "AZOTH_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        PostgresDatabase database = new PostgresDatabase(new DatabaseSettings(
                "ignored", 5432, "ignored", "ignored", "", false, 5, url));
        database.initializeSchema();
        return database;
    }
}
