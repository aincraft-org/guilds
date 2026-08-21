package org.aincraft.guilds.storage.persist;

import org.aincraft.guilds.territory.persist.SqlScripts;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuildStorageStoreTest {
    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private SqlGuildStorageStore store;
    private String guildId;

    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        ensureStorageSchema(services.databaseManager());
        store = new SqlGuildStorageStore(services.databaseManager(), Logger.getLogger("storage-test"));
        UUID mayor = UUID.randomUUID();
        services.residentService().createResident(mayor, "Mayor-" + mayor.toString().substring(0, 8));
        Guild guild = services.guildService().createGuild("Storage Guild " + UUID.randomUUID(), mayor);
        guildId = guild.getId();
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    private static void ensureStorageSchema(DatabaseManager databaseManager) {
        try (Connection connection = databaseManager.getConnection()) {
            if (!SqlSupport.columnExists(connection, "guild_storage_banks", "schema_version")) {
                dropStorageTables(connection);
                SqlScripts.apply(connection, "migrations/guilds/V24__guild-storage.sql");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure guild storage schema", e);
        }
    }

    private static void dropStorageTables(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS guild_storage_audit");
            statement.execute("DROP TABLE IF EXISTS guild_storage_slots");
            statement.execute("DROP TABLE IF EXISTS guild_storage_policies");
            statement.execute("DROP TABLE IF EXISTS guild_storage_tabs");
            statement.execute("DROP TABLE IF EXISTS guild_storage_banks");
        }
    }
    @Test
    void getOrCreateBankCreatesDefaultBankTabAndPolicy() {
        assertTrue(store.getBank(guildId).isEmpty());

        GuildStorageBank bank = store.getOrCreateBank(guildId);

        assertEquals(guildId, bank.guildId());
        assertEquals(SqlGuildStorageStore.CURRENT_SCHEMA_VERSION, bank.schemaVersion());
        assertTrue(store.getBank(guildId).isPresent());

        assertEquals(1, store.loadTabs(guildId).size());
        StorageTab tab = store.loadTabs(guildId).getFirst();
        assertEquals(SqlGuildStorageStore.DEFAULT_TAB_ID, tab.tabId());
        assertEquals(SqlGuildStorageStore.DEFAULT_TAB_CAPACITY, tab.capacitySlots());
        assertTrue(tab.unlocked());

        StoragePolicy policy = store.loadPolicy(guildId);
        assertEquals("MEMBER", policy.depositRole());
        assertEquals("ASSISTANT", policy.withdrawRole());
        assertEquals("MAYOR", policy.manageRole());
    }

    @Test
    void saveSlotRoundTripsPayloadAndIncrementsVersion() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-abc", "payload-bytes");

        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 3, payload, 0L));

        Map<Integer, StorageSlot> slots = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID);
        assertEquals(1, slots.size());
        StorageSlot slot = slots.get(3);
        assertEquals(payload, slot.item());
        assertEquals(1L, slot.version());

        OpaqueItemPayload updated = new OpaqueItemPayload("paper-bytes-v1", "fp-def", "updated-bytes");
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 3, updated, 1L));

        StorageSlot reloaded = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(3);
        assertEquals(updated, reloaded.item());
        assertEquals(2L, reloaded.version());
    }

    @Test
    void saveSlotRejectsMismatchedExpectedVersion() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-abc", "payload-bytes");
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 0L));

        assertFalse(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 0L));
        assertFalse(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 99L));

        StorageSlot slot = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(1);
        assertEquals(1L, slot.version());
        assertEquals(payload, slot.item());
    }

    @Test
    void saveSlotWithNullItemClearsOccupiedSlot() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-abc", "payload-bytes");
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 5, payload, 0L));
        assertFalse(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).isEmpty());

        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 5, null, 1L));
        assertTrue(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).isEmpty());
    }

    @Test
    void savePolicyPersistsUpdatedRoles() {
        store.getOrCreateBank(guildId);
        StoragePolicy updated = new StoragePolicy(
                guildId,
                "ASSISTANT",
                "MAYOR",
                "MAYOR",
                java.time.Instant.parse("2026-08-21T12:00:00Z"));

        store.savePolicy(updated);

        StoragePolicy loaded = store.loadPolicy(guildId);
        assertEquals("ASSISTANT", loaded.depositRole());
        assertEquals("MAYOR", loaded.withdrawRole());
        assertEquals("MAYOR", loaded.manageRole());
        assertEquals(updated.updatedAt(), loaded.updatedAt());
    }

    @Test
    void recordAuditPersistsAuditRow() throws Exception {
        store.getOrCreateBank(guildId);
        UUID actor = UUID.randomUUID();
        store.recordAudit(
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                7,
                "fp-abc",
                "facility-1");

        DatabaseManager databaseManager = services.databaseManager();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT actor_uuid, operation, tab_id, slot_index, fingerprint, facility_id
                     FROM guild_storage_audit
                     WHERE guild_id = ?
                     """)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(actor.toString(), result.getString("actor_uuid"));
                assertEquals("DEPOSIT", result.getString("operation"));
                assertEquals(SqlGuildStorageStore.DEFAULT_TAB_ID, result.getString("tab_id"));
                assertEquals(7, result.getInt("slot_index"));
                assertEquals("fp-abc", result.getString("fingerprint"));
                assertEquals("facility-1", result.getString("facility_id"));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void getOrCreateBankIsIdempotent() {
        GuildStorageBank first = store.getOrCreateBank(guildId);
        GuildStorageBank second = store.getOrCreateBank(guildId);

        assertEquals(first.guildId(), second.guildId());
        assertEquals(first.schemaVersion(), second.schemaVersion());
        assertEquals(first.createdAt(), second.createdAt());
        assertEquals(1, store.loadTabs(guildId).size());
        assertTrue(store.getBank(guildId).isPresent());
    }
}
