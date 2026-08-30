package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SqlStatementsHikariStoreTest {
    @Test
    void hikariDataSourceRunsResourceLoadedStoreSql() throws Exception {
        String url = System.getenv("GUILDS_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "GUILDS_TEST_JDBC_URL not set — skipping PostgreSQL integration test");

        DatabaseSettings settings = new DatabaseSettings(
                "127.0.0.1", 5432, "azoth_territory",
                envOr("GUILDS_TEST_JDBC_USER", "azoth"),
                envOr("GUILDS_TEST_JDBC_PASSWORD", "azoth"),
                false, 5, url);
        try (Database database = DatabaseFactory.open(settings)) {
            database.initializeSchema();
            try (Connection connection = database.dataSource().getConnection()) {
                assertTrue(connection.isValid(5));
            }

            String select = SqlStatements.load("territory/select.sql");
            assertTrue(select.startsWith("SELECT"));
            assertFalse(select.contains("{schema}"));

            PostgresTerritoryStore store = new PostgresTerritoryStore(database);
            TerritoryRegistry registry = new TerritoryRegistry();
            registry.register(new Territory(
                    "sql-resource-roundtrip",
                    "SQL Resource",
                    "world",
                    Boundary.ofPolygon(List.of(
                            new BlockPos(0, 0), new BlockPos(8, 0),
                            new BlockPos(8, 8), new BlockPos(0, 8)))));
            store.save(registry);

            TerritoryRegistry reloaded = new TerritoryRegistry();
            store.loadInto(reloaded);
            assertEquals("SQL Resource",
                    reloaded.get("sql-resource-roundtrip").orElseThrow().name());
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
