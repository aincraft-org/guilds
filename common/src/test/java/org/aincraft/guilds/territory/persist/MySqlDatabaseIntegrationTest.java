package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.MySqlTestDatabase;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlDatabaseIntegrationTest {
    @Test
    void territoryRoundTripUsesMysqlDialect() throws Exception {
        try (Database database = MySqlTestDatabase.open()) {
            assertEquals(DatabaseType.MYSQL, database.type());
            TerritoryRegistry registry = new TerritoryRegistry();
            registry.register(new Territory(
                    "mysql-smoke",
                    "MySQL Smoke",
                    "world",
                    Boundary.ofPolygon(List.of(
                            new BlockPos(0, 0), new BlockPos(16, 0),
                            new BlockPos(16, 16), new BlockPos(0, 16)))));
            PostgresTerritoryStore store = new PostgresTerritoryStore(database);
            store.save(registry);
            TerritoryRegistry loaded = new TerritoryRegistry();
            store.loadInto(loaded);
            assertEquals(1, loaded.size());
            assertEquals("MySQL Smoke", loaded.get("mysql-smoke").orElseThrow().name());
        }
    }
}
