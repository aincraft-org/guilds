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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.aincraft.guilds.storage.service.StorageResult;
import org.aincraft.guilds.storage.persist.StorageOperationLookupResult;
import org.aincraft.guilds.storage.persist.StorageOperationStatus;
import java.time.Instant;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        ensureOperationSchema(services.databaseManager());
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


    private static void ensureOperationSchema(DatabaseManager databaseManager) {
        try (Connection connection = databaseManager.getConnection()) {
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
            throw new IllegalStateException("Failed to ensure guild storage operation schema", e);
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

    @Test
    void saveSlotDetectsOptimisticLockConflict() throws Exception {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload initial = new OpaqueItemPayload("paper-bytes-v1", "fp-initial", "initial-bytes");
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, initial, 0L));

        OpaqueItemPayload firstUpdate = new OpaqueItemPayload("paper-bytes-v1", "fp-one", "first-bytes");
        OpaqueItemPayload secondUpdate = new OpaqueItemPayload("paper-bytes-v1", "fp-two", "second-bytes");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean firstResult = new AtomicBoolean();
        AtomicBoolean secondResult = new AtomicBoolean();

        Thread first = new Thread(() -> {
            ready.countDown();
            awaitLatch(start);
            firstResult.set(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, firstUpdate, 1L));
        });
        Thread second = new Thread(() -> {
            ready.countDown();
            awaitLatch(start);
            secondResult.set(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, secondUpdate, 1L));
        });
        first.start();
        second.start();
        ready.await();
        start.countDown();
        first.join();
        second.join();

        assertTrue(firstResult.get() ^ secondResult.get());
        StorageSlot slot = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(2);
        assertEquals(2L, slot.version());
        assertTrue(firstUpdate.equals(slot.item()) || secondUpdate.equals(slot.item()));
    }

    @Test
    void saveSlotAtomicDeleteRejectsStaleVersion() throws Exception {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-abc", "payload-bytes");
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, 0L));

        try (Connection connection = services.databaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE guild_storage_slots
                     SET version = ?
                     WHERE guild_id = ? AND tab_id = ? AND slot_index = ?
                     """)) {
            statement.setLong(1, 99L);
            statement.setString(2, guildId);
            statement.setString(3, SqlGuildStorageStore.DEFAULT_TAB_ID);
            statement.setInt(4, 4);
            assertEquals(1, statement.executeUpdate());
        }

        assertFalse(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, null, 1L));

        StorageSlot slot = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(4);
        assertEquals(99L, slot.version());
        assertEquals(payload, slot.item());
    }

    @Test
    void getOrCreateBankRetriesAfterConcurrentCreateRace() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<GuildStorageBank> first = new AtomicReference<>();
        AtomicReference<GuildStorageBank> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread creatorOne = new Thread(() -> runConcurrentBankCreate(ready, start, first, failure));
        Thread creatorTwo = new Thread(() -> runConcurrentBankCreate(ready, start, second, failure));
        creatorOne.start();
        creatorTwo.start();
        ready.await();
        start.countDown();
        creatorOne.join();
        creatorTwo.join();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent bank create failed", failure.get());
        }
        assertNotNull(first.get());
        assertNotNull(second.get());
        assertEquals(first.get().guildId(), second.get().guildId());
        assertEquals(first.get().schemaVersion(), second.get().schemaVersion());
        assertTrue(store.getBank(guildId).isPresent());
        assertEquals(1, store.loadTabs(guildId).size());
    }

    private void runConcurrentBankCreate(
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReference<GuildStorageBank> result,
            AtomicReference<Throwable> failure) {
        try {
            ready.countDown();
            awaitLatch(start);
            result.set(store.getOrCreateBank(guildId));
        } catch (Throwable throwable) {
            failure.set(throwable);
        }
    }


    @Test
    void depositWithAuditRollsBackSlotWhenAuditInsertFails() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-audit", "payload-bytes");
        UUID actor = UUID.randomUUID();
        store.simulateAuditFailureForTests = true;

        UUID operationId = UUID.randomUUID();
        SqlGuildStorageStore.DepositAuditOutcome outcome = store.depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 11, payload, actor, "facility-1", operationId);

        assertEquals(SqlGuildStorageStore.SlotMutationResult.FAILED, outcome.status());
        assertTrue(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).isEmpty());
    }

    @Test
    void depositWithAuditPersistsSlotAndAuditAtomically() throws Exception {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-atomic", "payload-bytes");
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        SqlGuildStorageStore.DepositAuditOutcome outcome = store.depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 12, payload, actor, "facility-atomic", operationId);

        assertEquals(SqlGuildStorageStore.SlotMutationResult.SUCCESS, outcome.status());
        assertEquals(payload, store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(12).item());

        try (Connection connection = services.databaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT operation, facility_id
                     FROM guild_storage_audit
                     WHERE guild_id = ? AND tab_id = ? AND slot_index = ?
                     """)) {
            statement.setString(1, guildId);
            statement.setString(2, SqlGuildStorageStore.DEFAULT_TAB_ID);
            statement.setInt(3, 12);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("DEPOSIT", result.getString("operation"));
                assertEquals("facility-atomic", result.getString("facility_id"));
            }
        }
    }

    @Test
    void hasMatchingAuditRequiresExactOperationId() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID otherOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-audit-op", "payload-bytes");
        Instant notBefore = Instant.parse("2026-08-21T12:00:00Z");

        store.depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 16, payload, actor, "facility-audit", operationId);

        assertTrue(store.hasMatchingAudit(
                operationId,
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                16,
                "facility-audit",
                notBefore,
                payload));
        assertFalse(store.hasMatchingAudit(
                otherOperationId,
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                16,
                "facility-audit",
                notBefore,
                payload));
    }


    @Test
    void hasMatchingAuditRequiresMatchingFingerprint() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-audit-op", "payload-bytes");
        OpaqueItemPayload otherFingerprint = new OpaqueItemPayload("paper-bytes-v1", "fp-other", "payload-bytes");
        Instant notBefore = Instant.parse("2026-08-21T12:00:00Z");

        store.depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 16, payload, actor, "facility-audit", operationId);

        assertFalse(store.hasMatchingAudit(
                operationId,
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                16,
                "facility-audit",
                notBefore,
                otherFingerprint));
    }

    @Test
    void hasMatchingAuditHonorsChronologicalNotBeforeBound() throws Exception {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-time", "payload-bytes");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");

        store.depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 17, payload, actor, "facility-time", operationId);

        try (Connection connection = services.databaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE guild_storage_audit
                     SET recorded_at = ?
                     WHERE operation_id = ?
                     """)) {
            statement.setString(1, "2026-08-21T12:00:01Z");
            statement.setString(2, operationId.toString());
            assertEquals(1, statement.executeUpdate());
        }

        assertFalse(store.hasMatchingAudit(
                operationId,
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                17,
                "facility-time",
                Instant.parse("2026-08-21T12:00:02Z"),
                payload));
        assertTrue(store.hasMatchingAudit(
                operationId,
                guildId,
                actor,
                "DEPOSIT",
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                17,
                "facility-time",
                createdAt,
                payload));
    }
    @Test
    void operationJournalPersistsAndReplaysCommittedResult() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-journal", "payload-bytes");
        StorageSlot slot = new StorageSlot(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                13,
                payload,
                1L,
                Instant.parse("2026-08-21T12:00:00Z"));

        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                actor,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                13,
                "facility-journal",
                payload));
        store.finalizeOperation(
                operationId,
                org.aincraft.guilds.storage.persist.StorageOperationStatus.COMMITTED,
                "SUCCESS",
                null,
                slot,
                payload);

        org.aincraft.guilds.storage.persist.StorageOperationRecord loaded =
                store.findOperation(operationId).orElseThrow();
        assertEquals(org.aincraft.guilds.storage.persist.StorageOperationStatus.COMMITTED, loaded.status());
        assertEquals("SUCCESS", loaded.resultStatus());
        assertEquals(payload, loaded.requestSnapshot());
        assertEquals(payload, loaded.resultItem());
        assertEquals(slot.version(), loaded.resultSlot().version());
    }

    @Test
    void pendingOperationPersistsRequestSnapshotForWithdrawRecovery() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-pending-withdraw", "payload-bytes");

        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "WITHDRAW",
                actor,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                14,
                "facility-withdraw",
                payload));

        StorageOperationRecord loaded = store.findOperation(operationId).orElseThrow();
        assertEquals(StorageOperationStatus.PENDING, loaded.status());
        assertEquals(payload, loaded.requestSnapshot());
        assertEquals(null, loaded.resultItem());
    }

    @Test
    void saveSlotRoundTripsPayloadLargerThanMysqlTextLimit() {
        store.getOrCreateBank(guildId);
        String largePayload = "x".repeat(70_000);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-large", largePayload);

        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 15, payload, 0L));

        StorageSlot slot = store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(15);
        assertEquals(largePayload, slot.item().payload());
    }

    @Test
    void finalizeOperationPreservesRequestSnapshotAfterCommit() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OpaqueItemPayload request = new OpaqueItemPayload("paper-bytes-v1", "fp-request", "request-bytes");
        OpaqueItemPayload result = new OpaqueItemPayload("paper-bytes-v1", "fp-result", "result-bytes");
        StorageSlot slot = new StorageSlot(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                17,
                result,
                1L,
                Instant.parse("2026-08-21T12:00:00Z"));

        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                actor,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                17,
                "facility-request",
                request));
        store.finalizeOperation(
                operationId,
                StorageOperationStatus.COMMITTED,
                "SUCCESS",
                null,
                slot,
                result);

        StorageOperationRecord loaded = store.findOperation(operationId).orElseThrow();
        assertEquals(request, loaded.requestSnapshot());
        assertEquals(result, loaded.resultItem());
    }

    @Test
    void finalizeOperationPreservesCommittedOutcomeWhenConnectionCloseFails() throws Exception {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload request = new OpaqueItemPayload("paper-bytes-v1", "fp-request", "request-bytes");
        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                UUID.randomUUID(),
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                20,
                "facility-close",
                request));

        DatabaseManager databaseManager = services.databaseManager();
        java.lang.reflect.Field field = DatabaseManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        javax.sql.DataSource originalDataSource = (javax.sql.DataSource) field.get(databaseManager);
        javax.sql.DataSource failingCloseDataSource = new javax.sql.DataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                Connection delegate = originalDataSource.getConnection();
                Connection connection = org.mockito.Mockito.spy(delegate);
                org.mockito.Mockito.doThrow(new java.sql.SQLException("close failed"))
                        .when(connection)
                        .close();
                return connection;
            }

            @Override
            public Connection getConnection(String username, String password) throws java.sql.SQLException {
                return getConnection();
            }

            @Override
            public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
                return originalDataSource.getLogWriter();
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) throws java.sql.SQLException {
                originalDataSource.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws java.sql.SQLException {
                originalDataSource.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws java.sql.SQLException {
                return originalDataSource.getLoginTimeout();
            }

            @Override
            public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
                return originalDataSource.getParentLogger();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                return originalDataSource.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
                return originalDataSource.isWrapperFor(iface);
            }
        };
        field.set(databaseManager, failingCloseDataSource);
        try {
            store.finalizeOperation(
                    operationId,
                    StorageOperationStatus.COMMITTED,
                    "SUCCESS",
                    null,
                    null,
                    request);
        } finally {
            field.set(databaseManager, originalDataSource);
        }

        StorageOperationRecord loaded = store.findOperation(operationId).orElseThrow();
        assertEquals(StorageOperationStatus.COMMITTED, loaded.status());
        assertEquals(request, loaded.resultItem());
    }

    @Test
    void finalizeOperationPreservesTerminalJournalStateWhenDowngradingToUnknown() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload request = new OpaqueItemPayload("paper-bytes-v1", "fp-request", "request-bytes");
        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                UUID.randomUUID(),
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                21,
                "facility-terminal",
                request));
        store.finalizeOperation(
                operationId,
                StorageOperationStatus.COMMITTED,
                "SUCCESS",
                null,
                null,
                request);

        store.finalizeOperation(
                operationId,
                StorageOperationStatus.UNKNOWN,
                StorageResult.Status.STORAGE_ERROR.name(),
                "fallback unknown",
                null,
                null);

        StorageOperationRecord loaded = store.findOperation(operationId).orElseThrow();
        assertEquals(StorageOperationStatus.COMMITTED, loaded.status());
        assertEquals("SUCCESS", loaded.resultStatus());
        assertEquals(request, loaded.resultItem());
    }

    @Test
    void finalizeOperationUpgradesUnknownToCommitted() {
        store.getOrCreateBank(guildId);
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload request = new OpaqueItemPayload("paper-bytes-v1", "fp-unknown-recover", "request-bytes");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageSlot reconstructed = new StorageSlot(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                22,
                request,
                1L,
                createdAt);
        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                UUID.randomUUID(),
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                22,
                "facility-unknown",
                request));
        store.finalizeOperation(
                operationId,
                StorageOperationStatus.UNKNOWN,
                StorageResult.Status.STORAGE_ERROR.name(),
                "Storage mutation outcome unknown; retry with same operationId",
                null,
                null);

        store.finalizeOperation(
                operationId,
                StorageOperationStatus.COMMITTED,
                StorageResult.Status.SUCCESS.name(),
                null,
                reconstructed,
                request);

        StorageOperationRecord loaded = store.findOperation(operationId).orElseThrow();
        assertEquals(StorageOperationStatus.COMMITTED, loaded.status());
        assertEquals(StorageResult.Status.SUCCESS.name(), loaded.resultStatus());
        assertEquals(request, loaded.resultItem());
        assertEquals(reconstructed, loaded.resultSlot());
    }


    @Test
    void lookupOperationDistinguishesNotFoundFromReadFailure() {
        UUID operationId = UUID.randomUUID();
        assertEquals(
                StorageOperationLookupResult.Status.NOT_FOUND,
                store.lookupOperation(operationId).status());

        store.getOrCreateBank(guildId);
        assertTrue(store.insertPendingOperation(
                operationId,
                guildId,
                "DEPOSIT",
                UUID.randomUUID(),
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                18,
                "facility-lookup",
                new OpaqueItemPayload("paper-bytes-v1", "fp-lookup", "payload")));
        assertEquals(
                StorageOperationLookupResult.Status.FOUND,
                store.lookupOperation(operationId).status());
    }

    @Test
    void depositWithAuditReturnsUnknownOnIndeterminateCommit() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-unknown-commit", "payload-bytes");
        store.simulateIndeterminateCommitForTests = true;

        SqlGuildStorageStore.DepositAuditOutcome outcome = store.depositWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                19,
                payload,
                UUID.randomUUID(),
                "facility-unknown",
                UUID.randomUUID());

        assertEquals(SqlGuildStorageStore.SlotMutationResult.UNKNOWN, outcome.status());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent test start", e);
        }
    }
    @Test
    void withdrawWithAuditCreatesPendingPayoutObligation() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-withdraw", "payload-bytes");
        UUID operationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 18, payload, 0L));

        SqlGuildStorageStore.WithdrawAuditOutcome outcome = store.withdrawWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                18,
                payload,
                1L,
                actor,
                "facility-withdraw",
                operationId);

        assertEquals(SqlGuildStorageStore.SlotMutationResult.SUCCESS, outcome.status());
        StoragePayoutObligationRecord obligation = store.findPayoutObligation(operationId).orElseThrow();
        assertEquals(StoragePayoutObligationStatus.PENDING, obligation.status());
        assertEquals(payload, obligation.item());
    }

    @Test
    void reinsertWithdrawPayoutWithAuditRestoresSlotAndMarksObligationReinserted() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-reinsert", "payload-bytes");
        UUID withdrawOperationId = UUID.randomUUID();
        UUID reinsertOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 19, payload, 0L));
        store.withdrawWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                19,
                payload,
                1L,
                actor,
                "facility-reinsert",
                withdrawOperationId);

        SqlGuildStorageStore.ReinsertWithdrawPayoutOutcome outcome = store.reinsertWithdrawPayoutWithAudit(
                withdrawOperationId,
                reinsertOperationId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                19,
                payload,
                actor,
                "facility-reinsert");

        assertEquals(SqlGuildStorageStore.SlotMutationResult.SUCCESS, outcome.status());
        assertEquals(payload, store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(19).item());
        StoragePayoutObligationRecord obligation = store.findPayoutObligation(withdrawOperationId).orElseThrow();
        assertEquals(StoragePayoutObligationStatus.REINSERTED, obligation.status());
        assertEquals(reinsertOperationId, obligation.reinsertOperationId());
    }


    @Test
    void reinsertWithdrawPayoutRejectsUnrelatedOccupiedSlot() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload withdrawn = new OpaqueItemPayload("paper-bytes-v1", "fp-withdrawn", "withdrawn-bytes");
        OpaqueItemPayload unrelated = new OpaqueItemPayload("paper-bytes-v1", "fp-unrelated", "unrelated-bytes");
        UUID withdrawOperationId = UUID.randomUUID();
        UUID reinsertOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 20, withdrawn, 0L));
        store.withdrawWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                20,
                withdrawn,
                1L,
                actor,
                "facility-reinsert",
                withdrawOperationId);
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 20, unrelated, 0L));

        SqlGuildStorageStore.ReinsertWithdrawPayoutOutcome outcome = store.reinsertWithdrawPayoutWithAudit(
                withdrawOperationId,
                reinsertOperationId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                20,
                withdrawn,
                actor,
                "facility-reinsert");

        assertEquals(SqlGuildStorageStore.SlotMutationResult.CONFLICT, outcome.status());
        assertEquals(unrelated, store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID).get(20).item());
        assertEquals(StoragePayoutObligationStatus.PENDING, store.findPayoutObligation(withdrawOperationId).orElseThrow().status());
    }

    @Test
    void payoutDeliveryClaimPreventsDuplicateGiveUntilMarkerConfirmed() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-delivery", "payload-bytes");
        UUID withdrawOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 21, payload, 0L));
        store.withdrawWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                21,
                payload,
                1L,
                actor,
                "facility-delivery",
                withdrawOperationId);

        java.util.Optional<java.util.UUID> deliveryToken = store.claimPayoutObligationForDelivery(withdrawOperationId);
        assertTrue(deliveryToken.isPresent());
        assertTrue(store.claimPayoutObligationForDelivery(withdrawOperationId).isEmpty());
        assertFalse(store.markPayoutObligationDelivered(withdrawOperationId, java.util.UUID.randomUUID()));
        assertTrue(store.markPayoutObligationDelivered(withdrawOperationId, deliveryToken.orElseThrow()));
        assertEquals(StoragePayoutObligationStatus.DELIVERED, store.findPayoutObligation(withdrawOperationId).orElseThrow().status());
    }

    @Test
    void cancelPayoutDeliveryBeforeReinsertionReleasesDeliveringClaim() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-cancel", "payload-bytes");
        UUID withdrawOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        assertTrue(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 22, payload, 0L));
        store.withdrawWithAudit(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                22,
                payload,
                1L,
                actor,
                "facility-cancel",
                withdrawOperationId);

        java.util.Optional<java.util.UUID> deliveryToken = store.claimPayoutObligationForDelivery(withdrawOperationId);
        assertTrue(deliveryToken.isPresent());
        assertTrue(store.cancelPayoutDeliveryForReinsertion(withdrawOperationId));
        assertEquals(StoragePayoutObligationStatus.PENDING, store.findPayoutObligation(withdrawOperationId).orElseThrow().status());
    }

    @Test
    void depositRestorationObligationPersistsUntilMarkedRestored() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-restore", "payload-bytes");
        UUID depositOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        store.insertDepositRestorationObligation(
                depositOperationId,
                guildId,
                actor,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                23,
                "facility-restore",
                payload);

        assertEquals(1, store.findPendingDepositRestorations().size());
        assertTrue(store.markDepositRestorationComplete(depositOperationId));
        assertTrue(store.findPendingDepositRestorations().isEmpty());
    }

    @Test
    void depositRestorationClaimAllowsOnlyOneConcurrentHandoff() {
        store.getOrCreateBank(guildId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-restore-claim", "payload-bytes");
        UUID depositOperationId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        store.insertDepositRestorationObligation(
                depositOperationId,
                guildId,
                actor,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                24,
                "facility-restore-claim",
                payload);

        assertTrue(store.claimDepositRestorationForDelivery(depositOperationId));
        assertFalse(store.claimDepositRestorationForDelivery(depositOperationId));
        assertTrue(store.releaseDepositRestorationClaim(depositOperationId));
        assertTrue(store.claimDepositRestorationForDelivery(depositOperationId));
        assertTrue(store.markDepositRestorationComplete(depositOperationId));
        assertTrue(store.findPendingDepositRestorations().isEmpty());
    }



}
