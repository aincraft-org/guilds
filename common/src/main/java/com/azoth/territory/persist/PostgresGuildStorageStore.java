package com.azoth.territory.persist;

import com.azoth.territory.storage.GuildStoragePolicy;
import com.azoth.territory.storage.GuildStorageSnapshot;
import com.azoth.territory.storage.OpaqueItemPayload;
import com.azoth.territory.storage.StorageAddress;
import com.azoth.territory.storage.StorageOperation;
import com.azoth.territory.storage.StorageRank;
import com.azoth.territory.storage.StorageResult;
import com.azoth.territory.storage.StorageStatus;
import com.azoth.territory.storage.StorageTab;
import com.azoth.territory.storage.StorageWithdrawResult;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional PostgreSQL store for guild storage banks, tabs, slots, policies,
 * and audit rows.
 *
 * <p>Every mutation commits the state change and its audit row atomically and
 * uses pessimistic row locks ({@code SELECT ... FOR UPDATE}) so concurrent slot
 * writers serialize: exactly one wins an empty slot and the rest observe the
 * committed occupant and report {@link StorageStatus#CONFLICT}. SQL failures
 * are converted to {@link IOException}; a snapshot is never partially updated.
 * Only bank, policy, and tab operations are idempotent ({@code ON CONFLICT}).</p>
 */
public final class PostgresGuildStorageStore implements AutoCloseable {
    private static final String GENERAL_TAB_ID = "general";

    private final PostgresDatabase database;
    private final int initialCapacitySlots;
    private final int expansionTabCapacitySlots;

    public PostgresGuildStorageStore(
            PostgresDatabase database,
            int initialCapacitySlots,
            int expansionTabCapacitySlots) {
        this.database = Objects.requireNonNull(database, "database");
        if (initialCapacitySlots <= 0) {
            throw new IllegalArgumentException(
                    "initialCapacitySlots must be positive, got " + initialCapacitySlots);
        }
        if (expansionTabCapacitySlots <= 0) {
            throw new IllegalArgumentException(
                    "expansionTabCapacitySlots must be positive, got " + expansionTabCapacitySlots);
        }
        this.initialCapacitySlots = initialCapacitySlots;
        this.expansionTabCapacitySlots = expansionTabCapacitySlots;
    }

    @Override
    public void close() {
        // The shared PostgresDatabase owns the pool lifecycle.
    }

    /**
     * Creates the bank, the default policy, and the {@code general} tab on first
     * call; later calls are no-ops. Returns the resulting snapshot.
     */
    public GuildStorageSnapshot ensureBank(String guildId) throws IOException {
        guildId = requireGuildId(guildId);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                insertBank(connection, guildId);
                insertInitialTab(connection, guildId, GENERAL_TAB_ID, "General", 0,
                        initialCapacitySlots);
                insertDefaultPolicy(connection, guildId, GuildStoragePolicy.defaults());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to ensure guild storage bank for guild " + guildId, e);
        }
        return load(guildId);
    }

    /** Loads the full snapshot for a guild; empty tabs/slots with the default policy when no bank exists. */
    public GuildStorageSnapshot load(String guildId) throws IOException {
        guildId = requireGuildId(guildId);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                List<StorageTab> tabs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT tab_id, display_name, ordinal, capacity_slots, unlocked "
                            + "FROM guild_storage_tabs WHERE guild_id = ? ORDER BY ordinal")) {
                statement.setString(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        tabs.add(new StorageTab(
                                result.getString("tab_id"),
                                result.getString("display_name"),
                                result.getInt("ordinal"),
                                result.getInt("capacity_slots"),
                                result.getBoolean("unlocked")));
                    }
                }
            }
            Map<StorageAddress, OpaqueItemPayload> occupiedSlots = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT tab_id, slot_index, item_schema, item_payload, item_fingerprint "
                            + "FROM guild_storage_slots WHERE guild_id = ? ORDER BY tab_id, slot_index")) {
                statement.setString(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        StorageAddress address = new StorageAddress(
                                guildId, result.getString("tab_id"), result.getInt("slot_index"));
                        occupiedSlots.put(address, new OpaqueItemPayload(
                                result.getString("item_schema"),
                                result.getString("item_payload"),
                                result.getString("item_fingerprint")));
                    }
                }
            }
            GuildStoragePolicy policy;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT deposit_rank, withdraw_rank, manage_rank "
                            + "FROM guild_storage_policies WHERE guild_id = ?")) {
                statement.setString(1, guildId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        policy = new GuildStoragePolicy(
                                StorageRank.valueOf(result.getString("deposit_rank")),
                                StorageRank.valueOf(result.getString("withdraw_rank")),
                                StorageRank.valueOf(result.getString("manage_rank")));
                    } else {
                        policy = GuildStoragePolicy.defaults();
                    }
                }
            }
                connection.commit();
                return new GuildStorageSnapshot(guildId, tabs, occupiedSlots, policy);
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load guild storage for guild " + guildId, e);
        }
    }

    /** Upserts the guild policy idempotently and writes a MANAGE audit row atomically. */
    public StorageResult setPolicy(String guildId, GuildStoragePolicy policy,
                                   UUID actorUuid, String facilityId) throws IOException {
        guildId = requireGuildId(guildId);
        facilityId = requireAudit(actorUuid, facilityId);
        Objects.requireNonNull(policy, "policy");
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                insertPolicy(connection, guildId, policy);
                insertAudit(connection, guildId, actorUuid, StorageOperation.MANAGE,
                        GENERAL_TAB_ID, null, null, null, facilityId);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to set storage policy for guild " + guildId, e);
        }
        return new StorageResult(StorageStatus.SUCCESS, "Storage policy updated");
    }

    /**
     * Deposits a payload into the addressed slot. Validates the address against
     * the locked tab before any write; returns {@link StorageStatus#CONFLICT}
     * when the slot is already occupied and {@link StorageStatus#INVALID_ITEM}
     * for payload or address validation failures. Slot write and audit row
     * commit atomically.
     */
    public StorageResult put(String guildId, StorageAddress address, OpaqueItemPayload payload,
                             UUID actorUuid, String facilityId) throws IOException {
        guildId = requireGuildId(guildId);
        facilityId = requireAudit(actorUuid, facilityId);
        Objects.requireNonNull(address, "address");
        StorageStatus validation = validatePayload(payload);
        if (validation != StorageStatus.SUCCESS) {
            return new StorageResult(validation, "Invalid item payload");
        }
        if (!guildId.equals(address.guildId())) {
            return new StorageResult(StorageStatus.INVALID_ITEM,
                    "Address guild does not match storage guild");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int capacity = lockTab(connection, guildId, address.tabId());
                if (capacity < 0) {
                    rollback(connection);
                    return new StorageResult(StorageStatus.INVALID_ITEM, "Unknown tab " + address.tabId());
                }
                if (address.slotIndex() >= capacity) {
                    rollback(connection);
                    return new StorageResult(StorageStatus.INVALID_ITEM,
                            "Slot index " + address.slotIndex() + " exceeds tab capacity " + capacity);
                }
                if (slotOccupied(connection, guildId, address.tabId(), address.slotIndex())) {
                    rollback(connection);
                    return new StorageResult(StorageStatus.CONFLICT, "Slot is already occupied");
                }
                insertSlot(connection, guildId, address.tabId(), address.slotIndex(), payload);
                insertAudit(connection, guildId, actorUuid, StorageOperation.DEPOSIT,
                        address.tabId(), address.slotIndex(), payload.schema(), payload.fingerprint(), facilityId);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to store item for guild " + guildId, e);
        }
        return new StorageResult(StorageStatus.SUCCESS, "Item stored");
    }

    /**
     * Withdraws the payload from the addressed slot. The tab row is locked so
     * concurrent mutations serialize; the removed payload, slot delete, and
     * audit row commit atomically.
     */
    public StorageWithdrawResult remove(String guildId, StorageAddress address,
                                        UUID actorUuid, String facilityId) throws IOException {
        guildId = requireGuildId(guildId);
        facilityId = requireAudit(actorUuid, facilityId);
        Objects.requireNonNull(address, "address");
        if (!guildId.equals(address.guildId())) {
            return new StorageWithdrawResult(StorageStatus.INVALID_ITEM,
                    "Address guild does not match storage guild", Optional.empty());
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int capacity = lockTab(connection, guildId, address.tabId());
                if (capacity < 0) {
                    rollback(connection);
                    return new StorageWithdrawResult(StorageStatus.INVALID_ITEM,
                            "Unknown tab " + address.tabId(), Optional.empty());
                }
                if (address.slotIndex() >= capacity) {
                    rollback(connection);
                    return new StorageWithdrawResult(StorageStatus.INVALID_ITEM,
                            "Slot index " + address.slotIndex() + " exceeds tab capacity " + capacity,
                            Optional.empty());
                }
                OpaqueItemPayload payload = readSlot(connection, guildId, address.tabId(), address.slotIndex());
                if (payload == null) {
                    rollback(connection);
                    return new StorageWithdrawResult(StorageStatus.CONFLICT,
                            "Slot is empty", Optional.empty());
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM guild_storage_slots "
                                + "WHERE guild_id = ? AND tab_id = ? AND slot_index = ?")) {
                    statement.setString(1, guildId);
                    statement.setString(2, address.tabId());
                    statement.setInt(3, address.slotIndex());
                    statement.executeUpdate();
                }
                insertAudit(connection, guildId, actorUuid, StorageOperation.WITHDRAW,
                        address.tabId(), address.slotIndex(), payload.schema(), payload.fingerprint(), facilityId);
                connection.commit();
                return new StorageWithdrawResult(StorageStatus.SUCCESS, "Item withdrawn",
                        Optional.of(payload));
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to withdraw item for guild " + guildId, e);
        }
    }

    /**
     * Unlocks (creates or refreshes) a named tab idempotently and writes a
     * MANAGE audit row atomically.
     */
    public StorageResult unlockTab(String guildId, String tabId, String displayName,
                                   int ordinal, int capacitySlots,
                                   UUID actorUuid, String facilityId) throws IOException {
        guildId = requireGuildId(guildId);
        facilityId = requireAudit(actorUuid, facilityId);
        if (tabId == null || tabId.trim().isEmpty()) {
            return new StorageResult(StorageStatus.INVALID_ITEM, "Tab id must not be blank");
        }
        tabId = tabId.trim();
        if (displayName == null) {
            return new StorageResult(StorageStatus.INVALID_ITEM, "Display name is required");
        }
        if (ordinal < 0) {
            return new StorageResult(StorageStatus.INVALID_ITEM,
                    "Ordinal must be non-negative, got " + ordinal);
        }
        if (capacitySlots <= 0) {
            return new StorageResult(StorageStatus.INVALID_ITEM,
                    "Capacity must be positive, got " + capacitySlots);
        }
        if (capacitySlots != expansionTabCapacitySlots) {
            return new StorageResult(StorageStatus.INVALID_ITEM,
                    "Expansion tabs unlock at the configured size " + expansionTabCapacitySlots
                            + ", got " + capacitySlots);
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                insertTab(connection, guildId, tabId, displayName, ordinal, capacitySlots);
                insertAudit(connection, guildId, actorUuid, StorageOperation.MANAGE,
                        tabId, null, null, null, facilityId);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to unlock tab for guild " + guildId, e);
        }
        return new StorageResult(StorageStatus.SUCCESS, "Tab unlocked");
    }

    /** Normalizes a guild id: rejects null/blank and returns the trimmed form. */
    private static String requireGuildId(String guildId) {
        Objects.requireNonNull(guildId, "guildId");
        String trimmed = guildId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("guildId must not be blank");
        }
        return trimmed;
    }

    /** Normalizes audit inputs: rejects null/blank facility and returns the trimmed form. */
    private static String requireAudit(UUID actorUuid, String facilityId) {
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(facilityId, "facilityId");
        String trimmed = facilityId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        return trimmed;
    }

    /** Validates the payload fields before any SQL; never inspects item semantics. */
    private static StorageStatus validatePayload(OpaqueItemPayload payload) {
        if (payload == null) {
            return StorageStatus.INVALID_ITEM;
        }
        if (payload.schema().trim().isEmpty() || payload.fingerprint().trim().isEmpty()) {
            return StorageStatus.INVALID_ITEM;
        }
        if (payload.payloadJson().isBlank()) {
            return StorageStatus.INVALID_ITEM;
        }
        try {
            JsonParser.parseString(payload.payloadJson());
        } catch (RuntimeException e) {
            return StorageStatus.INVALID_ITEM;
        }
        return StorageStatus.SUCCESS;
    }

    private static void insertBank(Connection connection, String guildId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_banks (guild_id, schema_version, created_at, updated_at) "
                        + "VALUES (?, 1, NOW(), NOW()) ON CONFLICT (guild_id) DO NOTHING")) {
            statement.setString(1, guildId);
            statement.executeUpdate();
        }
    }

    private static void insertPolicy(Connection connection, String guildId,
                                     GuildStoragePolicy policy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_policies (guild_id, deposit_rank, withdraw_rank, manage_rank, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (guild_id) DO UPDATE SET "
                        + "deposit_rank = EXCLUDED.deposit_rank, "
                        + "withdraw_rank = EXCLUDED.withdraw_rank, "
                        + "manage_rank = EXCLUDED.manage_rank, "
                        + "updated_at = NOW()")) {
            statement.setString(1, guildId);
            statement.setString(2, policy.depositRank().name());
            statement.setString(3, policy.withdrawRank().name());
            statement.setString(4, policy.manageRank().name());
            statement.executeUpdate();
        }
    }

    /**
     * Insert-only default policy creation: the explicit {@code setPolicy} upsert
     * is the only operation that may change an existing policy, so repeated
     * {@code ensureBank} calls never overwrite custom thresholds.
     */
    private static void insertDefaultPolicy(Connection connection, String guildId,
                                            GuildStoragePolicy policy) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_policies (guild_id, deposit_rank, withdraw_rank, manage_rank, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (guild_id) DO NOTHING")) {
            statement.setString(1, guildId);
            statement.setString(2, policy.depositRank().name());
            statement.setString(3, policy.withdrawRank().name());
            statement.setString(4, policy.manageRank().name());
            statement.executeUpdate();
        }
    }

    private static void insertTab(Connection connection, String guildId, String tabId,
                                  String displayName, int ordinal, int capacitySlots) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_tabs "
                        + "(guild_id, tab_id, display_name, ordinal, capacity_slots, unlocked) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE) ON CONFLICT (guild_id, tab_id) DO UPDATE SET "
                        + "display_name = EXCLUDED.display_name, "
                        + "ordinal = EXCLUDED.ordinal, "
                        + "capacity_slots = EXCLUDED.capacity_slots, "
                        + "unlocked = TRUE")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setString(3, displayName);
            statement.setInt(4, ordinal);
            statement.setInt(5, capacitySlots);
            statement.executeUpdate();
        }
    }

    /**
     * Insert-only initial tab creation: existing tabs keep their capacity and
     * metadata, so a changed constructor default only applies to the first
     * bank/tab creation. Expansion via {@code unlockTab} keeps its explicit
     * upsert semantics.
     */
    private static void insertInitialTab(Connection connection, String guildId, String tabId,
                                         String displayName, int ordinal, int capacitySlots) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_tabs "
                        + "(guild_id, tab_id, display_name, ordinal, capacity_slots, unlocked) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE) ON CONFLICT (guild_id, tab_id) DO NOTHING")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setString(3, displayName);
            statement.setInt(4, ordinal);
            statement.setInt(5, capacitySlots);
            statement.executeUpdate();
        }
    }

    /** Locks the tab row; returns its capacity, or -1 when the tab does not exist. */
    private static int lockTab(Connection connection, String guildId, String tabId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT capacity_slots FROM guild_storage_tabs "
                        + "WHERE guild_id = ? AND tab_id = ? FOR UPDATE")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : -1;
            }
        }
    }

    private static boolean slotOccupied(Connection connection, String guildId,
                                        String tabId, int slotIndex) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM guild_storage_slots "
                        + "WHERE guild_id = ? AND tab_id = ? AND slot_index = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static OpaqueItemPayload readSlot(Connection connection, String guildId,
                                              String tabId, int slotIndex) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT item_schema, item_payload, item_fingerprint FROM guild_storage_slots "
                        + "WHERE guild_id = ? AND tab_id = ? AND slot_index = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new OpaqueItemPayload(
                        result.getString("item_schema"),
                        result.getString("item_payload"),
                        result.getString("item_fingerprint"));
            }
        }
    }

    private static void insertSlot(Connection connection, String guildId, String tabId,
                                   int slotIndex, OpaqueItemPayload payload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_slots "
                        + "(guild_id, tab_id, slot_index, item_schema, item_fingerprint, item_payload, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW())")) {
            statement.setString(1, guildId);
            statement.setString(2, tabId);
            statement.setInt(3, slotIndex);
            statement.setString(4, payload.schema());
            statement.setString(5, payload.fingerprint());
            statement.setString(6, payload.payloadJson());
            statement.executeUpdate();
        }
    }

    private static void insertAudit(Connection connection, String guildId, UUID actorUuid,
                                    StorageOperation operation, String tabId, Integer slotIndex,
                                    String itemSchema, String itemFingerprint,
                                    String facilityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO guild_storage_audit "
                        + "(guild_id, actor_uuid, operation, tab_id, slot_index, item_schema, item_fingerprint, facility_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
            statement.setString(1, guildId);
            statement.setObject(2, actorUuid);
            statement.setString(3, operation.name());
            statement.setString(4, tabId);
            if (slotIndex == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, slotIndex);
            }
            if (itemSchema == null) {
                statement.setNull(6, java.sql.Types.VARCHAR);
            } else {
                statement.setString(6, itemSchema);
            }
            if (itemFingerprint == null) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, itemFingerprint);
            }
            statement.setString(8, facilityId);
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            // The original failure is the one reported.
        }
    }
}
