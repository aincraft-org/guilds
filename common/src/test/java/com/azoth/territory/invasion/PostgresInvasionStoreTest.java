package com.azoth.territory.invasion;

import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.persist.PostgresDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class PostgresInvasionStoreTest {
    private PostgresDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    @Test
    void roundTripPreservesCompleteInvasionState() throws Exception {
        UUID invasionId = UUID.randomUUID();
        UUID entityA = UUID.randomUUID();
        UUID entityB = UUID.randomUUID();
        InvasionRecord active = new InvasionRecord(invasionId, "guild-a", "Guild A", "world_nether",
                1.25, 64.0, -8.5, InvasionStatus.ACTIVE, 2, List.of(entityA, entityB),
                new GuildDamage(17, 42), 1_700_000_001L);
        InvasionRecord terminal = new InvasionRecord(UUID.randomUUID(), "guild-b", "Guild B", "world",
                -3.0, 70.5, 12.0, InvasionStatus.DEVASTATED, 0, List.of(),
                new GuildDamage(99, 100), 1_700_000_002L);
        PostgresInvasionStore store = new PostgresInvasionStore(database);

        store.save(List.of(active, terminal));

        assertEquals(List.of(active, terminal), new PostgresInvasionStore(database).load());
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        insertDocument("{\"version\":99,\"guildDamage\":{},\"invasions\":[]}");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new PostgresInvasionStore(database).load());
        IOException cause = assertInstanceOf(IOException.class, failure.getCause());
        assertEquals("unsupported invasion state version: 99", cause.getMessage());
    }
    @Test
    void rejectsUnsupportedVersionWithWrappedIOException() throws Exception {
        insertDocument("{\"version\":99,\"guildDamage\":{},\"invasions\":[]}");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new PostgresInvasionStore(database).load());
        IOException cause = assertInstanceOf(IOException.class, failure.getCause());
        assertEquals("unsupported invasion state version: 99", cause.getMessage());
    }

    @Test
    void rejectsNonStringIdentifiers() throws Exception {
        insertDocument("""
                {"version":1,"guildDamage":{},"invasions":[{
                "invasionId":true,"guildId":"g","guildName":"G","worldId":"w",
                "x":0,"y":0,"z":0,"status":"ACTIVE","wave":0,
                "currentWaveEntities":[],"damage":{"destroyedBlocks":0,"percent":0},"updatedAt":1}]}""");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new PostgresInvasionStore(database).load());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void rejectsMalformedGuildDamage() throws Exception {
        for (String document : List.of(
                "{\"version\":1,\"guildDamage\":{\"g\":null},\"invasions\":[]}",
                "{\"version\":1,\"guildDamage\":{\"g\":{\"destroyedBlocks\":-1,\"percent\":0}},\"invasions\":[]}",
                "{\"version\":1,\"guildDamage\":{\"g\":{\"destroyedBlocks\":1,\"percent\":-1}},\"invasions\":[]}",
                "{\"version\":1,\"guildDamage\":{\"g\":{\"destroyedBlocks\":1.5,\"percent\":1}},\"invasions\":[]}")) {
            insertDocument(document);
            assertThrows(IllegalStateException.class, () -> new PostgresInvasionStore(database).load());
        }
    }

    private void insertDocument(String document) throws Exception {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO invasion_state (id, doc) VALUES (1, ?::jsonb) ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc")) {
            statement.setString(1, document);
            statement.executeUpdate();
        }
    }
}
