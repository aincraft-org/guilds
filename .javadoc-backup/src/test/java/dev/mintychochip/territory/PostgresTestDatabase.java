package dev.mintychochip.territory;

import dev.mintychochip.territory.persist.DatabaseSettings;
import dev.mintychochip.territory.persist.PostgresDatabase;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;

/** Opens the opt-in PostgreSQL integration database used by paper tests. */
public final class PostgresTestDatabase {
    private PostgresTestDatabase() {
    }

    public static PostgresDatabase open() {
        String url = System.getenv("AZOTH_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set AZOTH_TEST_JDBC_URL to run PostgreSQL integration tests");
        DatabaseSettings settings = new DatabaseSettings(
                "127.0.0.1", 5432, "azoth_territory", "azoth",
                System.getenv().getOrDefault("AZOTH_TEST_JDBC_PASSWORD", ""),
                false, 4, url);
        try {
            PostgresDatabase database = new PostgresDatabase(settings);
            database.initializeSchema();
            return database;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize PostgreSQL test database", e);
        }
    }
}
