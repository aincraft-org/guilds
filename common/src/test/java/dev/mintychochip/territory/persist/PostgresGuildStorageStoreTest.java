package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.storage.GuildStorageDocument;
import dev.mintychochip.territory.storage.OpaqueItemPayload;
import dev.mintychochip.territory.storage.StorageSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresGuildStorageStoreTest {
    private PostgresDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        try (var connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM guild_storage_banks");
        }
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void roundTripPreservesSlotsAndRevision() throws Exception {
        PostgresGuildStorageStore store = new PostgresGuildStorageStore(database);
        GuildStorageDocument document = new GuildStorageDocument("guild-1", 54, 3, List.of(
                new StorageSlot(2, new OpaqueItemPayload("paper-itemstack-bytes-v1", "fp", "c3RvbmU="))));

        store.save(document);

        assertEquals(document, store.load("guild-1").orElseThrow());
    }
}
