package org.aincraft.guilds.storage.persist;

import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transactional SQL persistence for guild item storage banks.
 */
public class SqlGuildStorageStore {
    public static final String DEFAULT_TAB_ID = "general";
    public static final int DEFAULT_TAB_CAPACITY = 54;
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String DEFAULT_DEPOSIT_ROLE = "MEMBER";
    private static final String DEFAULT_WITHDRAW_ROLE = "ASSISTANT";
    private static final String DEFAULT_MANAGE_ROLE = "MAYOR";

    private final DatabaseManager databaseManager;
    private final Logger logger;

    /** Test hook: next atomic mutation fails while inserting audit. */
    public volatile boolean simulateAuditFailureForTests;

    public enum SlotMutationResult {
        SUCCESS,
        CONFLICT,
        FAILED
    }

    public record DepositAuditOutcome(SlotMutationResult status, StorageSlot slot) {}

    public record WithdrawAuditOutcome(SlotMutationResult status, OpaqueItemPayload item) {}

    public SqlGuildStorageStore(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public Optional<GuildStorageBank> getBank(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = databaseManager.getConnection()) {
            return selectBank(connection, guildId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load guild storage bank for " + guildId, e);
            return Optional.empty();
        }
    }

    public GuildStorageBank getOrCreateBank(String guildId) {
        requireGuildId(guildId);
        Optional<GuildStorageBank> existing = getBank(guildId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Optional<GuildStorageBank> created = databaseManager.executeTransactionWithResult(connection -> {
            Optional<GuildStorageBank> bank = selectBank(connection, guildId);
            if (bank.isPresent()) {
                return bank.get();
            }
            Instant now = Instant.now();
            String nowText = now.toString();
            insertBank(connection, guildId, CURRENT_SCHEMA_VERSION, nowText);
            insertTab(connection, guildId, DEFAULT_TAB_ID, "General", 0, DEFAULT_TAB_CAPACITY, true);
            insertPolicy(connection, guildId, DEFAULT_DEPOSIT_ROLE, DEFAULT_WITHDRAW_ROLE, DEFAULT_MANAGE_ROLE, nowText);
            return new GuildStorageBank(guildId, CURRENT_SCHEMA_VERSION, now, now);
        });
        if (created.isPresent()) {
            return created.get();
        }
        return getBank(guildId).orElseThrow(() -> new IllegalStateException("Failed to create guild storage bank for " + guildId));
    }

    public List<StorageTab> loadTabs(String guildId) {
        requireGuildId(guildId);
        List<StorageTab> tabs = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT tab_id, display_name, ordinal, capacity_slots, unlocked
                     FROM guild_storage_tabs
                     WHERE guild_id = ?
                     ORDER BY ordinal, tab_id
                     """)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tabs.add(new StorageTab(
                            guildId,
                            result.getString("tab_id"),
                            result.getString("display_name"),
                            result.getInt("ordinal"),
                            result.getInt("capacity_slots"),
                            result.getBoolean("unlocked")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load storage tabs for guild " + guildId, e);
        }
        return List.copyOf(tabs);
    }

    public Map<Integer, StorageSlot> loadSlots(String guildId, String tabId) {
        requireGuildId(guildId);
        requireTabId(tabId);
        Map<Integer, StorageSlot> slots = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT slot_index, item_schema, item_fingerprint, item_payload, version, updated_at
                     FROM guild_storage_slots
                     WHERE guild_id = ? AND tab_id = ?
                     ORDER BY slot_index
                     """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int slotIndex = result.getInt("slot_index");
                    OpaqueItemPayload item = new OpaqueItemPayload(
                            result.getString("item_schema"),
                            result.getString("item_fingerprint"),
                            result.getString("item_payload"));
                    slots.put(slotIndex, new StorageSlot(
                            guildId,
                            tabId,
                            slotIndex,
                            item,
                            result.getLong("version"),
                            parseInstant(result.getString("updated_at"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load storage slots for guild " + guildId + " tab " + tabId, e);
        }
        return Map.copyOf(slots);
    }

    public boolean saveSlot(String guildId, String tabId, int slotIndex, OpaqueItemPayload item, long expectedVersion) {
        requireGuildId(guildId);
        requireTabId(tabId);
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        return databaseManager.executeTransactionWithResult(connection -> {
            Long currentVersion = selectSlotVersion(connection, guildId, tabId, slotIndex);
            if (currentVersion == null) {
                if (expectedVersion != 0L) {
                    return false;
                }
                if (item == null) {
                    return true;
                }
                insertSlot(connection, guildId, tabId, slotIndex, item, 1L, Instant.now());
                return true;
            }
            if (currentVersion != expectedVersion) {
                return false;
            }
            if (item == null) {
                return deleteSlot(connection, guildId, tabId, slotIndex, expectedVersion);
            }
            return updateSlot(
                    connection,
                    guildId,
                    tabId,
                    slotIndex,
                    item,
                    expectedVersion,
                    expectedVersion + 1L,
                    Instant.now());
        }).orElse(false);
    }

    public StoragePolicy loadPolicy(String guildId) {
        requireGuildId(guildId);
        try (Connection connection = databaseManager.getConnection()) {
            Optional<StoragePolicy> policy = selectPolicy(connection, guildId);
            if (policy.isPresent()) {
                return policy.get();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load storage policy for guild " + guildId, e);
        }
        return new StoragePolicy(
                guildId,
                DEFAULT_DEPOSIT_ROLE,
                DEFAULT_WITHDRAW_ROLE,
                DEFAULT_MANAGE_ROLE,
                Instant.EPOCH);
    }

    public void savePolicy(StoragePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        boolean committed = databaseManager.executeTransaction(connection -> upsertPolicy(
                connection,
                policy.guildId(),
                policy.depositRole(),
                policy.withdrawRole(),
                policy.manageRole(),
                policy.updatedAt().toString()));
        if (!committed) {
            throw new IllegalStateException("Failed to save storage policy for guild " + policy.guildId());
        }
    }

    public void recordAudit(
            String guildId,
            UUID actorUuid,
            String operation,
            String tabId,
            Integer slotIndex,
            String fingerprint,
            String facilityId) {
        requireGuildId(guildId);
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        boolean committed = databaseManager.executeTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO guild_storage_audit (
                        id, guild_id, actor_uuid, operation, tab_id, slot_index, fingerprint, facility_id, recorded_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, guildId);
                if (actorUuid == null) {
                    statement.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(3, actorUuid.toString());
                }
                statement.setString(4, operation.trim());
                if (tabId == null || tabId.isBlank()) {
                    statement.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(5, tabId.trim());
                }
                if (slotIndex == null) {
                    statement.setNull(6, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(6, slotIndex);
                }
                if (fingerprint == null || fingerprint.isBlank()) {
                    statement.setNull(7, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(7, fingerprint.trim());
                }
                if (facilityId == null || facilityId.isBlank()) {
                    statement.setNull(8, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(8, facilityId.trim());
                }
                statement.setString(9, Instant.now().toString());
                statement.executeUpdate();
            }
        });
        if (!committed) {
            throw new IllegalStateException("Failed to record storage audit for guild " + guildId);
        }
    }


    public DepositAuditOutcome depositWithAudit(
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            UUID actorUuid,
            String facilityId) {
        requireGuildId(guildId);
        requireTabId(tabId);
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(actorUuid, "actorUuid");
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId is required");
        }
        Optional<DepositAuditOutcome> outcome = databaseManager.executeTransactionWithResult(connection -> {
            Long currentVersion = selectSlotVersion(connection, guildId, tabId, slotIndex);
            if (currentVersion != null) {
                return new DepositAuditOutcome(SlotMutationResult.CONFLICT, null);
            }
            Instant now = Instant.now();
            insertSlot(connection, guildId, tabId, slotIndex, item, 1L, now);
            insertAudit(
                    connection,
                    guildId,
                    actorUuid,
                    "DEPOSIT",
                    tabId,
                    slotIndex,
                    item.fingerprint(),
                    facilityId.trim());
            return new DepositAuditOutcome(
                    SlotMutationResult.SUCCESS, new StorageSlot(guildId, tabId, slotIndex, item, 1L, now));
        });
        return outcome.orElse(new DepositAuditOutcome(SlotMutationResult.FAILED, null));
    }

    public WithdrawAuditOutcome withdrawWithAudit(
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload expectedItem,
            long expectedVersion,
            UUID actorUuid,
            String facilityId) {
        requireGuildId(guildId);
        requireTabId(tabId);
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(expectedItem, "expectedItem");
        Objects.requireNonNull(actorUuid, "actorUuid");
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId is required");
        }
        Optional<WithdrawAuditOutcome> outcome = databaseManager.executeTransactionWithResult(connection -> {
            Optional<StorageSlot> occupied = selectSlot(connection, guildId, tabId, slotIndex);
            if (occupied.isEmpty()) {
                return new WithdrawAuditOutcome(SlotMutationResult.CONFLICT, null);
            }
            StorageSlot slot = occupied.get();
            if (slot.version() != expectedVersion || !slot.item().equals(expectedItem)) {
                return new WithdrawAuditOutcome(SlotMutationResult.CONFLICT, null);
            }
            if (!deleteSlot(connection, guildId, tabId, slotIndex, expectedVersion)) {
                return new WithdrawAuditOutcome(SlotMutationResult.CONFLICT, null);
            }
            insertAudit(
                    connection,
                    guildId,
                    actorUuid,
                    "WITHDRAW",
                    tabId,
                    slotIndex,
                    expectedItem.fingerprint(),
                    facilityId.trim());
            return new WithdrawAuditOutcome(SlotMutationResult.SUCCESS, expectedItem);
        });
        return outcome.orElse(new WithdrawAuditOutcome(SlotMutationResult.FAILED, null));
    }

    public Optional<StorageOperationRecord> findOperation(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = databaseManager.getConnection()) {
            return selectOperation(connection, operationId.toString());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load storage operation " + operationId, e);
        }
    }

    public List<StorageOperationRecord> findPendingOperations() {
        return findOperationsByStatus(StorageOperationStatus.PENDING);
    }

    /**
     * Returns whether durable audit evidence exists for a storage mutation at the given slot.
     * Used to reconcile interrupted operation journal rows against committed slot/audit state.
     */
    public boolean hasMatchingAudit(
            String guildId,
            UUID actorUuid,
            String operation,
            String tabId,
            int slotIndex,
            String facilityId,
            Instant notBefore) {
        requireGuildId(guildId);
        Objects.requireNonNull(actorUuid, "actorUuid");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        requireTabId(tabId);
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId is required");
        }
        Objects.requireNonNull(notBefore, "notBefore");
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1
                     FROM guild_storage_audit
                     WHERE guild_id = ?
                       AND actor_uuid = ?
                       AND operation = ?
                       AND tab_id = ?
                       AND slot_index = ?
                       AND facility_id = ?
                       AND recorded_at >= ?
                     LIMIT 1
                     """)) {
            statement.setString(1, guildId.trim());
            statement.setString(2, actorUuid.toString());
            statement.setString(3, operation.trim());
            statement.setString(4, tabId.trim());
            statement.setInt(5, slotIndex);
            statement.setString(6, facilityId.trim());
            statement.setString(7, notBefore.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query storage audit evidence for guild " + guildId, e);
        }
    }

    public boolean insertPendingOperation(
            UUID operationId,
            String guildId,
            String operationType,
            UUID actorUuid,
            String tabId,
            int slotIndex,
            String facilityId) {
        Objects.requireNonNull(operationId, "operationId");
        requireGuildId(guildId);
        if (operationType == null || operationType.isBlank()) {
            throw new IllegalArgumentException("operationType is required");
        }
        Objects.requireNonNull(actorUuid, "actorUuid");
        requireTabId(tabId);
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId is required");
        }
        Instant now = Instant.now();
        Optional<Boolean> inserted = databaseManager.executeTransactionWithResult(connection -> {
            if (selectOperation(connection, operationId.toString()).isPresent()) {
                return false;
            }
            insertPendingOperationRow(
                    connection,
                    operationId.toString(),
                    guildId,
                    operationType.trim(),
                    actorUuid.toString(),
                    tabId,
                    slotIndex,
                    facilityId.trim(),
                    now);
            return true;
        });
        return inserted.orElse(false);
    }

    public void finalizeOperation(
            UUID operationId,
            StorageOperationStatus status,
            String resultStatus,
            String resultError,
            StorageSlot resultSlot,
            OpaqueItemPayload resultItem) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(status, "status");
        Instant now = Instant.now();
        boolean committed = databaseManager.executeTransaction(connection -> updateOperationResult(
                connection,
                operationId.toString(),
                status.name(),
                resultStatus,
                resultError,
                resultSlot,
                resultItem,
                now));
        if (!committed) {
            throw new IllegalStateException("Failed to finalize storage operation " + operationId);
        }
    }

    private List<StorageOperationRecord> findOperationsByStatus(StorageOperationStatus status) {
        Objects.requireNonNull(status, "status");
        List<StorageOperationRecord> operations = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT operation_id, guild_id, operation_type, actor_uuid, tab_id, slot_index,
                            facility_id, status, result_status, result_error,
                            result_item_schema, result_item_fingerprint, result_item_payload,
                            result_slot_version, result_slot_updated_at, created_at, updated_at
                     FROM guild_storage_operations
                     WHERE status = ?
                     ORDER BY created_at
                     """)) {
            statement.setString(1, status.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    operations.add(mapOperation(result));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load storage operations in status " + status, e);
        }
        return List.copyOf(operations);
    }

    private static Optional<StorageOperationRecord> selectOperation(Connection connection, String operationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, guild_id, operation_type, actor_uuid, tab_id, slot_index,
                       facility_id, status, result_status, result_error,
                       result_item_schema, result_item_fingerprint, result_item_payload,
                       result_slot_version, result_slot_updated_at, created_at, updated_at
                FROM guild_storage_operations
                WHERE operation_id = ?
                """)) {
            statement.setString(1, operationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapOperation(result));
            }
        }
    }

    private static void insertPendingOperationRow(
            Connection connection,
            String operationId,
            String guildId,
            String operationType,
            String actorUuid,
            String tabId,
            int slotIndex,
            String facilityId,
            Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_operations (
                    operation_id, guild_id, operation_type, actor_uuid, tab_id, slot_index,
                    facility_id, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, operationId);
            statement.setString(2, guildId);
            statement.setString(3, operationType);
            statement.setString(4, actorUuid);
            statement.setString(5, tabId);
            statement.setInt(6, slotIndex);
            statement.setString(7, facilityId);
            statement.setString(8, StorageOperationStatus.PENDING.name());
            statement.setString(9, timestamp.toString());
            statement.setString(10, timestamp.toString());
            statement.executeUpdate();
        }
    }

    private static void updateOperationResult(
            Connection connection,
            String operationId,
            String status,
            String resultStatus,
            String resultError,
            StorageSlot resultSlot,
            OpaqueItemPayload resultItem,
            Instant updatedAt) throws SQLException {
        OpaqueItemPayload itemForSnapshot = resultItem;
        if (itemForSnapshot == null && resultSlot != null) {
            itemForSnapshot = resultSlot.item();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_operations
                SET status = ?, result_status = ?, result_error = ?,
                    result_item_schema = ?, result_item_fingerprint = ?, result_item_payload = ?,
                    result_slot_version = ?, result_slot_updated_at = ?, updated_at = ?
                WHERE operation_id = ?
                """)) {
            statement.setString(1, status);
            if (resultStatus == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, resultStatus);
            }
            if (resultError == null) {
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(3, resultError);
            }
            if (itemForSnapshot == null) {
                statement.setNull(4, java.sql.Types.VARCHAR);
                statement.setNull(5, java.sql.Types.VARCHAR);
                statement.setNull(6, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, itemForSnapshot.schema());
                statement.setString(5, itemForSnapshot.fingerprint());
                statement.setString(6, itemForSnapshot.payload());
            }
            if (resultSlot == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
                statement.setNull(8, java.sql.Types.VARCHAR);
            } else {
                statement.setLong(7, resultSlot.version());
                statement.setString(8, resultSlot.updatedAt().toString());
            }
            statement.setString(9, updatedAt.toString());
            statement.setString(10, operationId);
            statement.executeUpdate();
        }
    }

    private static StorageOperationRecord mapOperation(ResultSet result) throws SQLException {
        OpaqueItemPayload resultItem = null;
        String itemSchema = result.getString("result_item_schema");
        if (itemSchema != null && !itemSchema.isBlank()) {
            resultItem = new OpaqueItemPayload(
                    itemSchema,
                    result.getString("result_item_fingerprint"),
                    result.getString("result_item_payload"));
        }
        StorageSlot resultSlot = null;
        long slotVersion = result.getLong("result_slot_version");
        if (!result.wasNull() && resultItem != null) {
            resultSlot = new StorageSlot(
                    result.getString("guild_id"),
                    result.getString("tab_id"),
                    result.getInt("slot_index"),
                    resultItem,
                    slotVersion,
                    parseInstant(result.getString("result_slot_updated_at")));
        }
        return new StorageOperationRecord(
                UUID.fromString(result.getString("operation_id")),
                result.getString("guild_id"),
                result.getString("operation_type"),
                UUID.fromString(result.getString("actor_uuid")),
                result.getString("tab_id"),
                result.getInt("slot_index"),
                result.getString("facility_id"),
                StorageOperationStatus.valueOf(result.getString("status")),
                result.getString("result_status"),
                result.getString("result_error"),
                resultItem,
                resultSlot,
                parseInstant(result.getString("created_at")),
                parseInstant(result.getString("updated_at")));
    }

    private static Optional<StorageSlot> selectSlot(
            Connection connection, String guildId, String tabId, int slotIndex) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slot_index, item_schema, item_fingerprint, item_payload, version, updated_at
                FROM guild_storage_slots
                WHERE guild_id = ? AND tab_id = ? AND slot_index = ?
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                OpaqueItemPayload item = new OpaqueItemPayload(
                        result.getString("item_schema"),
                        result.getString("item_fingerprint"),
                        result.getString("item_payload"));
                return Optional.of(new StorageSlot(
                        guildId,
                        tabId,
                        slotIndex,
                        item,
                        result.getLong("version"),
                        parseInstant(result.getString("updated_at"))));
            }
        }
    }

    private void insertAudit(
            Connection connection,
            String guildId,
            UUID actorUuid,
            String operation,
            String tabId,
            Integer slotIndex,
            String fingerprint,
            String facilityId) throws SQLException {
        if (simulateAuditFailureForTests) {
            simulateAuditFailureForTests = false;
            throw new SQLException("simulated audit failure");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_audit (
                    id, guild_id, actor_uuid, operation, tab_id, slot_index, fingerprint, facility_id, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, guildId);
            statement.setString(3, actorUuid.toString());
            statement.setString(4, operation.trim());
            statement.setString(5, tabId.trim());
            statement.setInt(6, slotIndex);
            if (fingerprint == null || fingerprint.isBlank()) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, fingerprint.trim());
            }
            statement.setString(8, facilityId.trim());
            statement.setString(9, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private static Optional<GuildStorageBank> selectBank(Connection connection, String guildId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT schema_version, created_at, updated_at
                FROM guild_storage_banks
                WHERE guild_id = ?
                """)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GuildStorageBank(
                        guildId,
                        result.getInt("schema_version"),
                        parseInstant(result.getString("created_at")),
                        parseInstant(result.getString("updated_at"))));
            }
        }
    }

    private static Optional<StoragePolicy> selectPolicy(Connection connection, String guildId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT deposit_role, withdraw_role, manage_role, updated_at
                FROM guild_storage_policies
                WHERE guild_id = ?
                """)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoragePolicy(
                        guildId,
                        result.getString("deposit_role"),
                        result.getString("withdraw_role"),
                        result.getString("manage_role"),
                        parseInstant(result.getString("updated_at"))));
            }
        }
    }

    private static Long selectSlotVersion(Connection connection, String guildId, String tabId, int slotIndex)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version
                FROM guild_storage_slots
                WHERE guild_id = ? AND tab_id = ? AND slot_index = ?
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return result.getLong("version");
            }
        }
    }

    private static void insertBank(Connection connection, String guildId, int schemaVersion, String timestamp)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_banks (guild_id, schema_version, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, guildId);
            statement.setInt(2, schemaVersion);
            statement.setString(3, timestamp);
            statement.setString(4, timestamp);
            statement.executeUpdate();
        }
    }

    private static void insertTab(
            Connection connection,
            String guildId,
            String tabId,
            String displayName,
            int ordinal,
            int capacitySlots,
            boolean unlocked) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_tabs (
                    guild_id, tab_id, display_name, ordinal, capacity_slots, unlocked
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setString(3, displayName);
            statement.setInt(4, ordinal);
            statement.setInt(5, capacitySlots);
            statement.setBoolean(6, unlocked);
            statement.executeUpdate();
        }
    }

    private static void insertPolicy(
            Connection connection,
            String guildId,
            String depositRole,
            String withdrawRole,
            String manageRole,
            String updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_policies (
                    guild_id, deposit_role, withdraw_role, manage_role, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, depositRole);
            statement.setString(3, withdrawRole);
            statement.setString(4, manageRole);
            statement.setString(5, updatedAt);
            statement.executeUpdate();
        }
    }

    private static void upsertPolicy(
            Connection connection,
            String guildId,
            String depositRole,
            String withdrawRole,
            String manageRole,
            String updatedAt) throws SQLException {
        String sql = SqlSupport.upsertSql(connection, """
                INSERT INTO guild_storage_policies (
                    guild_id, deposit_role, withdraw_role, manage_role, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """, "guild_id", """
                deposit_role = EXCLUDED.deposit_role,
                withdraw_role = EXCLUDED.withdraw_role,
                manage_role = EXCLUDED.manage_role,
                updated_at = EXCLUDED.updated_at
                """);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            statement.setString(2, depositRole);
            statement.setString(3, withdrawRole);
            statement.setString(4, manageRole);
            statement.setString(5, updatedAt);
            statement.executeUpdate();
        }
    }

    private static void insertSlot(
            Connection connection,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            long version,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_slots (
                    guild_id, tab_id, slot_index, item_schema, item_fingerprint, item_payload, version, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            statement.setString(4, item.schema());
            statement.setString(5, item.fingerprint());
            statement.setString(6, item.payload());
            statement.setLong(7, version);
            statement.setString(8, updatedAt.toString());
            statement.executeUpdate();
        }
    }

    private static boolean updateSlot(
            Connection connection,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            long expectedVersion,
            long newVersion,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_slots
                SET item_schema = ?, item_fingerprint = ?, item_payload = ?, version = ?, updated_at = ?
                WHERE guild_id = ? AND tab_id = ? AND slot_index = ? AND version = ?
                """)) {
            statement.setString(1, item.schema());
            statement.setString(2, item.fingerprint());
            statement.setString(3, item.payload());
            statement.setLong(4, newVersion);
            statement.setString(5, updatedAt.toString());
            statement.setString(6, guildId);
            statement.setString(7, tabId);
            statement.setInt(8, slotIndex);
            statement.setLong(9, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean deleteSlot(
            Connection connection, String guildId, String tabId, int slotIndex, long expectedVersion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM guild_storage_slots
                WHERE guild_id = ? AND tab_id = ? AND slot_index = ? AND version = ?
                """)) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            statement.setLong(4, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        return Instant.parse(value);
    }

    private static void requireGuildId(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }

    private static void requireTabId(String tabId) {
        if (tabId == null || tabId.isBlank()) {
            throw new IllegalArgumentException("tabId is required");
        }
    }
}
