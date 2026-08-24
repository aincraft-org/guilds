package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.service.impl.GuildStorageServiceImpl;
import org.aincraft.guilds.territory.persist.SqlScripts;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildStorageServiceIntegrationTest {
    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private SqlGuildStorageStore store;
    private String guildId;
    private UUID mayorId;

    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        ensureSchemas(services.databaseManager());
        store = new SqlGuildStorageStore(services.databaseManager(), Logger.getLogger("storage-integration"));
        mayorId = UUID.randomUUID();
        services.residentService().createResident(mayorId, "Mayor-" + mayorId.toString().substring(0, 8));
        Guild guild = services.guildService().createGuild("Storage Guild " + UUID.randomUUID(), mayorId);
        guildId = guild.getId();
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    @Test
    void duplicateOperationReturnsDeterministicResultAcrossReconstructedService() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-durable", "payload-bytes");
        GuildStorageServiceImpl first = serviceInstance();
        StorageResult<StorageSlot> firstResult = first.deposit(
                operationId,
                mayorId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                7,
                payload,
                "facility-1");
        assertTrue(firstResult.isSuccess(), () -> firstResult.status() + ": " + firstResult.errorMessage());

        GuildStorageServiceImpl second = serviceInstance();
        StorageResult<StorageSlot> replayed = second.deposit(
                operationId,
                mayorId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                7,
                payload,
                "facility-1");

        assertTrue(replayed.isSuccess());
        assertEquals(firstResult.value().orElseThrow().version(), replayed.value().orElseThrow().version());
        assertEquals(1, store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).size());
        assertEquals(payload, store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(7).item());
    }

    private GuildStorageServiceImpl serviceInstance() {
        return GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store,
                services.guildService(),
                services.residentService(),
                StorageFacilityAccessValidator.permitAll());
    }

    private static void ensureSchemas(DatabaseManager databaseManager) {
        try (Connection connection = databaseManager.getConnection()) {
            if (!SqlSupport.columnExists(connection, "guild_storage_banks", "schema_version")) {
                SqlScripts.apply(connection, "migrations/guilds/V24__guild-storage.sql");
            }
            if (!SqlSupport.columnExists(connection, "guild_storage_operations", "operation_id")) {
                SqlScripts.apply(connection, "migrations/guilds/V25__guild-storage-operations.sql");
            }
            if (!SqlSupport.columnExists(connection, "guild_storage_audit", "operation_id")) {
                SqlScripts.apply(connection, "migrations/guilds/V26__guild-storage-audit-operation.sql");
            }
            if (!SqlSupport.columnExists(connection, "guild_storage_operations", "request_item_schema")) {
                SqlScripts.apply(connection, "migrations/guilds/V27__guild-storage-operation-request-snapshot.sql");
            }
            if (!SqlSupport.tableExists(connection, "guild_storage_payout_obligations")) {
                SqlScripts.apply(connection, "migrations/guilds/V29__guild-storage-payout-obligations.sql");
            }
            if (!SqlSupport.columnExists(connection, "guild_storage_payout_obligations", "delivery_token")) {
                SqlScripts.apply(connection, "migrations/guilds/V30__guild-storage-payout-handoff-and-deposit-restoration.sql");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure guild storage schemas", e);
        }
    }
}
