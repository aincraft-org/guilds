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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO invasion_state (id, doc) VALUES (1, ?::jsonb) ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc")) {
            statement.setString(1, "{\"version\":99,\"guildDamage\":{},\"invasions\":[]}");
            statement.executeUpdate();
        }

        IOException failure = assertThrows(IOException.class, () -> new PostgresInvasionStore(database).load());
        assertEquals("unsupported invasion state version", failure.getMessage());
    }
}
