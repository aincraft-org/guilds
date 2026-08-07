package com.azoth.territory.persist;

import com.azoth.territory.storage.GuildStoragePolicy;
import com.azoth.territory.storage.GuildStorageSnapshot;
import com.azoth.territory.storage.OpaqueItemPayload;
import com.azoth.territory.storage.StorageAddress;
import com.azoth.territory.storage.StorageRank;
import com.azoth.territory.storage.StorageResult;
import com.azoth.territory.storage.StorageStatus;
import com.azoth.territory.storage.StorageTab;
import com.azoth.territory.storage.StorageWithdrawResult;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integration tests against a real PostgreSQL server only. */
class PostgresGuildStorageStoreTest {
    private static final String TEST_URL = System.getenv("AZOTH_TEST_JDBC_URL");
    private static PostgresDatabase database;
    private static PostgresGuildStorageStore store;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(TEST_URL != null && !TEST_URL.isBlank(),
                "AZOTH_TEST_JDBC_URL not set — skipping PostgreSQL integration test");
        database = new PostgresDatabase(new DatabaseSettings(
                "ignored", 5432, "ignored", "ignored", "", false, 5, TEST_URL));
        database.initializeSchema();
        database.initializeSchema();
        store = new PostgresGuildStorageStore(database, 54, 54);
    }

    @AfterAll
    static void disconnect() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void ensureBankCreatesDefaultBankPolicyAndTabIdempotently() throws Exception {
        String guildId = uniqueGuild("defaults");

        GuildStorageSnapshot first = store.ensureBank(guildId);
        GuildStorageSnapshot second = store.ensureBank(guildId);

        assertEquals(guildId, first.guildId());
        assertEquals(second, store.load(guildId));
        assertEquals(1, second.tabs().size());
        StorageTab general = second.tabs().get(0);
        assertEquals("general", general.id());
        assertEquals("General", general.displayName());
        assertEquals(0, general.ordinal());
        assertEquals(54, general.capacitySlots());
        assertEquals(GuildStoragePolicy.defaults(), second.policy());
        assertEquals(1, count("guild_storage_banks", "guild_id = ?", guildId));
        assertEquals(1, count("guild_storage_tabs", "guild_id = ?", guildId));
        assertEquals(1, count("guild_storage_policies", "guild_id = ?", guildId));
    }

    @Test
    void customPolicySurvivesRepeatedEnsureBank() throws Exception {
        String guildId = uniqueGuild("policy");
        GuildStoragePolicy custom = new GuildStoragePolicy(
                StorageRank.ASSISTANT, StorageRank.MAYOR, StorageRank.MAYOR);

        store.ensureBank(guildId);
        assertEquals(StorageStatus.SUCCESS,
                store.setPolicy(guildId, custom, UUID.randomUUID(), "facility-policy").status());

        assertEquals(custom, store.ensureBank(guildId).policy());
    }

    @Test
    void existingGeneralCapacityAndMetadataSurviveChangedDefault() throws Exception {
        String guildId = uniqueGuild("capacity");
        UUID actor = UUID.randomUUID();

        store.ensureBank(guildId);
        assertEquals(StorageStatus.SUCCESS,
                store.unlockTab(guildId, "general", "Custom General", 0, 54,
                        actor, "facility-capacity").status());

        PostgresGuildStorageStore changedDefaults = new PostgresGuildStorageStore(database, 99, 54);
        GuildStorageSnapshot loaded = changedDefaults.ensureBank(guildId);
        StorageTab general = loaded.tabs().get(0);
        assertEquals("Custom General", general.displayName());
        assertEquals(54, general.capacitySlots());
        assertEquals(0, general.ordinal());
    }

    @Test
    void textualIdentifiersAreTrimmedAtStoreBoundary() throws Exception {
        String guildId = uniqueGuild("trimmed");
        String tabId = "tab-trimmed";
        String facilityId = "facility-trimmed";
        UUID actor = UUID.randomUUID();

        store.ensureBank("  " + guildId + "  ");
        assertEquals(StorageStatus.SUCCESS,
                store.unlockTab(" " + guildId + " ", " " + tabId + " ",
                        "Trimmed", 1, 54, actor, " " + facilityId + " ").status());
        StorageAddress address = new StorageAddress(" " + guildId + " ", " " + tabId + " ", 0);
        assertEquals(StorageStatus.SUCCESS,
                store.put(" " + guildId + " ", address,
                        new OpaqueItemPayload("schema", "{\"trimmed\":true}", "fp-trimmed"),
                        actor, " " + facilityId + " ").status());

        GuildStorageSnapshot loaded = store.load(" " + guildId + " ");
        assertEquals(guildId, loaded.guildId());
        assertTrue(loaded.tabs().stream().anyMatch(tab -> tab.id().equals(tabId)));
        assertTrue(loaded.occupiedSlots().containsKey(new StorageAddress(guildId, tabId, 0)));
        assertEquals(2, count("guild_storage_audit",
                "guild_id = ? AND facility_id = ?", guildId, facilityId));
    }

    @Test
    void nullAddressPutReturnsInvalidItem() throws Exception {
        String guildId = uniqueGuild("null-put");
        UUID actor = UUID.randomUUID();
        String facility = "facility-null-put";
        OpaqueItemPayload payload = new OpaqueItemPayload("schema", "{\"value\":1}", "fingerprint");

        store.ensureBank(guildId);
        int auditBefore = count("guild_storage_audit", "guild_id = ?", guildId);

        StorageResult result = store.put(guildId, null, payload, actor, facility);

        assertEquals(StorageStatus.INVALID_ITEM, result.status());
        assertEquals(auditBefore, count("guild_storage_audit", "guild_id = ?", guildId));
    }

    @Test
    void nullAddressRemoveReturnsInvalidItemWithEmptyPayload() throws Exception {
        String guildId = uniqueGuild("null-remove");
        UUID actor = UUID.randomUUID();
        String facility = "facility-null-remove";

        store.ensureBank(guildId);
        int auditBefore = count("guild_storage_audit", "guild_id = ?", guildId);

        StorageWithdrawResult result = store.remove(guildId, null, actor, facility);

        assertEquals(StorageStatus.INVALID_ITEM, result.status());
        assertTrue(result.payload().isEmpty());
        assertEquals(auditBefore, count("guild_storage_audit", "guild_id = ?", guildId));
    }

    @Test
    void opaquePayloadPolicyTabUnlockAndAuditRoundTrip() throws Exception {
        String guildId = uniqueGuild("roundtrip");
        UUID actor = UUID.randomUUID();
        String facility = "facility-roundtrip";
        OpaqueItemPayload payload = new OpaqueItemPayload(
                "azoth:item:v1", "{\"material\":\"DIAMOND\",\"amount\":3}", "fp-roundtrip");
        StorageAddress address = new StorageAddress(guildId, "general", 0);
        GuildStoragePolicy policy = new GuildStoragePolicy(
                StorageRank.ASSISTANT, StorageRank.MAYOR, StorageRank.MAYOR);

        store.ensureBank(guildId);
        assertEquals(StorageStatus.SUCCESS,
                store.setPolicy(guildId, policy, actor, facility).status());
        assertEquals(StorageStatus.SUCCESS,
                store.put(guildId, address, payload, actor, facility).status());
        assertEquals(StorageStatus.SUCCESS,
                store.unlockTab(guildId, "expansion-1", "Materials", 1, 54, actor, facility).status());

        GuildStorageSnapshot loaded = store.load(guildId);
        assertEquals(policy, loaded.policy());
        assertEquals(2, loaded.tabs().size());
        assertEquals(54, loaded.tabs().get(1).capacitySlots());
        assertPayloadEquals(payload, loaded.occupiedSlots().get(address));

        StorageWithdrawResult removed = store.remove(guildId, address, actor, facility);
        assertEquals(StorageStatus.SUCCESS, removed.status());
        assertTrue(removed.payload().isPresent());
        assertPayloadEquals(payload, removed.payload().orElseThrow());
        assertFalse(store.load(guildId).occupiedSlots().containsKey(address));

        assertTrue(count("guild_storage_audit", "guild_id = ? AND facility_id = ?", guildId, facility) >= 4);
        assertEquals(0, count("guild_storage_slots", "guild_id = ?", guildId));
    }

    @Test
    void duplicateAndInvalidSlotLeaveStateAndAuditUnchanged() throws Exception {
        String guildId = uniqueGuild("rollback");
        UUID actor = UUID.randomUUID();
        String facility = "facility-rollback";
        OpaqueItemPayload payload = new OpaqueItemPayload("schema", "{\"value\":1}", "fingerprint");
        StorageAddress occupied = new StorageAddress(guildId, "general", 3);
        StorageAddress invalid = new StorageAddress(guildId, "general", 54);

        store.ensureBank(guildId);
        assertEquals(StorageStatus.SUCCESS,
                store.put(guildId, occupied, payload, actor, facility).status());
        int auditBefore = count("guild_storage_audit", "guild_id = ?", guildId);

        StorageResult duplicate = store.put(guildId, occupied, payload, actor, facility);
        StorageResult invalidResult = store.put(guildId, invalid, payload, actor, facility);

        assertEquals(StorageStatus.CONFLICT, duplicate.status());
        assertEquals(StorageStatus.INVALID_ITEM, invalidResult.status());
        assertPayloadEquals(payload, store.load(guildId).occupiedSlots().get(occupied));
        assertEquals(auditBefore, count("guild_storage_audit", "guild_id = ?", guildId));
        assertEquals(1, count("guild_storage_slots", "guild_id = ?", guildId));
    }

    @Test
    void concurrentPutsToOccupiedSlotReturnOneConflict() throws Exception {
        String guildId = uniqueGuild("concurrent");
        StorageAddress address = new StorageAddress(guildId, "general", 7);
        store.ensureBank(guildId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<StorageResult>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int attempt = i;
                attempts.add(() -> store.put(guildId, address,
                        new OpaqueItemPayload("schema", "{\"attempt\":" + attempt + "}", "fp-" + attempt),
                        UUID.randomUUID(), "facility-concurrent"));
            }
            List<Future<StorageResult>> futures = executor.invokeAll(attempts);
            List<StorageResult> results = new ArrayList<>();
            for (Future<StorageResult> future : futures) {
                results.add(future.get());
            }
            assertEquals(1, results.stream().filter(r -> r.status() == StorageStatus.SUCCESS).count());
            assertEquals(1, results.stream().filter(r -> r.status() == StorageStatus.CONFLICT).count());
            assertEquals(1, count("guild_storage_slots", "guild_id = ?", guildId));
            assertEquals(1, count("guild_storage_audit", "guild_id = ?", guildId));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String uniqueGuild(String suffix) {
        return "task3-" + suffix + "-" + UUID.randomUUID();
    }

    /** Schema and fingerprint are exact; JSONB normalizes the payload text, so compare JSON semantics. */
    private static void assertPayloadEquals(OpaqueItemPayload expected, OpaqueItemPayload actual) {
        assertEquals(expected.schema(), actual.schema());
        assertEquals(expected.fingerprint(), actual.fingerprint());
        assertEquals(JsonParser.parseString(expected.payloadJson()),
                JsonParser.parseString(actual.payloadJson()));
    }

    private static int count(String table, String where, Object... values) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + where;
        try (Connection connection = database.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }
}
