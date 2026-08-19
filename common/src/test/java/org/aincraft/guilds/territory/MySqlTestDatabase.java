package org.aincraft.guilds.territory;

import org.aincraft.guilds.territory.persist.Database;
import org.aincraft.guilds.territory.persist.DatabaseFactory;
import org.aincraft.guilds.territory.persist.DatabaseSettings;
import org.aincraft.guilds.territory.persist.DatabaseSettingsLoader;
import org.aincraft.guilds.territory.persist.DatabaseType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opens the opt-in MySQL integration database used by persistence tests. */
public final class MySqlTestDatabase {
    private MySqlTestDatabase() {
    }

    public static Database open() throws IOException {
        String url = firstEnv("GUILDS_TEST_MYSQL_JDBC_URL", "AZOTH_TEST_MYSQL_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set GUILDS_TEST_MYSQL_JDBC_URL to run MySQL integration tests");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database.type", "mysql");
        cfg.put("database.jdbc-url", url);
        cfg.put("database.user", firstEnv("GUILDS_TEST_MYSQL_USER", "MYSQL_USER"));
        cfg.put("database.password", firstEnv("GUILDS_TEST_MYSQL_PASSWORD", "MYSQL_PASSWORD"));
        if (cfg.get("database.user") == null) {
            cfg.put("database.user", "guilds");
        }
        if (cfg.get("database.password") == null) {
            cfg.put("database.password", "guilds");
        }
        DatabaseSettings settings = DatabaseSettingsLoader.fromValues(cfg);
        assumeTrue(settings.type() == DatabaseType.MYSQL, "MySQL settings required");
        Database database = DatabaseFactory.open(settings);
        database.initializeSchema();
        return database;
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
