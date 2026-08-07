package com.azoth.territory.persist;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test against a real PostgreSQL server.
 */
class PostgresTerritoryStoreTest {
    private static final String TEST_URL = System.getenv("AZOTH_TEST_JDBC_URL");
    private static PostgresDatabase database;
    private static PostgresTerritoryStore store;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(TEST_URL != null && !TEST_URL.isBlank(),
                "AZOTH_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = new PostgresDatabase(new DatabaseSettings(
                "ignored", 5432, "ignored", "ignored", "", false, 5, TEST_URL));
        database.initializeSchema();
        store = new PostgresTerritoryStore(database);
    }

    @AfterAll
    static void disconnect() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)))));
        store.save(registry);

        TerritoryRegistry reloaded = new TerritoryRegistry();
        store.loadInto(reloaded);
        assertEquals(1, reloaded.size());
        Optional<Territory> t = reloaded.get("everfall");
        assertTrue(t.isPresent());
        assertEquals("Everfall", t.get().name());
        assertEquals("world", t.get().worldId());
    }

    @Test
    void saveReplacesEntireRegistry() throws Exception {
        TerritoryRegistry first = new TerritoryRegistry();
        first.register(new Territory("alpha", "Alpha", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10)))));
        first.register(new Territory("beta", "Beta", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(100, 100), new BlockPos(110, 100),
                        new BlockPos(110, 110), new BlockPos(100, 110)))));
        store.save(first);

        TerritoryRegistry second = new TerritoryRegistry();
        second.register(new Territory("gamma", "Gamma", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(200, 200), new BlockPos(210, 200),
                        new BlockPos(210, 210), new BlockPos(200, 210)))));
        store.save(second);

        TerritoryRegistry reloaded = new TerritoryRegistry();
        store.loadInto(reloaded);
        assertEquals(List.of("gamma"), reloaded.list().stream().map(Territory::id).toList());
    }

    @Test
    void sharedSchemaInitIsIdempotent() throws Exception {
        database.initializeSchema();
        TerritoryRegistry reloaded = new TerritoryRegistry();
        store.loadInto(reloaded);
        assertTrue(reloaded.size() >= 0);
    }
}
