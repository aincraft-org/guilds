package dev.mintychochip.territory.standing;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.persist.PostgresDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresStandingStoreTest {
    private static final String TEST_URL = System.getenv("AZOTH_TEST_JDBC_URL");
    private static PostgresDatabase database;
    private static PostgresStandingStore store;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(TEST_URL != null && !TEST_URL.isBlank(),
                "AZOTH_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = PostgresTestDatabase.open();
        store = new PostgresStandingStore(database);
    }

    @AfterAll
    static void disconnect() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        StandingState state = new StandingState();
        StandingEntry entry = new StandingEntry();
        entry.ownerGuildId = "everfall-town";
        entry.bars.put("everfall-town", 200.5);
        state.entries.put("everfall", entry);
        store.save(state);

        StandingState reloaded = store.load();
        StandingEntry loaded = reloaded.entries.get("everfall");
        assertTrue(loaded != null, "entry must survive reload");
        assertEquals("everfall-town", loaded.ownerGuildId);
        assertEquals(200.5, loaded.bars.get("everfall-town"), 0.001);
    }

    @Test
    void loadWithNoStoredState_isEmpty() throws Exception {
        // Clear the shared single-row table first, then load must return empty.
        store.save(new StandingState());
        StandingState reloaded = store.load();
        assertTrue(reloaded.entries.isEmpty());
    }

    @Test
    void saveReplacesPreviousState() throws Exception {
        StandingState first = new StandingState();
        StandingEntry entry = new StandingEntry();
        entry.ownerGuildId = "g1";
        entry.bars.put("g1", 10.0);
        first.entries.put("t1", entry);
        store.save(first);

        StandingState second = new StandingState();
        StandingEntry entry2 = new StandingEntry();
        entry2.ownerGuildId = "g2";
        entry2.bars.put("g2", 20.0);
        second.entries.put("t2", entry2);
        store.save(second);

        StandingState reloaded = store.load();
        assertEquals(1, reloaded.entries.size());
        assertTrue(reloaded.entries.containsKey("t2"));
    }
}
