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

    /** Test hook: next atomic mutation returns indeterminate commit outcome. */
    public volatile boolean simulateIndeterminateCommitForTests;

    public enum SlotMutationResult {
        SUCCESS,
        CONFLICT,
        FAILED,
        UNKNOWN
    }

    public record DepositAuditOutcome(SlotMutationResult status, StorageSlot slot) {}

    public record WithdrawAuditOutcome(SlotMutationResult status, OpaqueItemPayload item) {}

    public record ReinsertWithdrawPayoutOutcome(SlotMutationResult status, StorageSlot slot) {}

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
                        id, operation_id, guild_id, actor_uuid, operation, tab_id, slot_index, fingerprint, facility_id, recorded_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setNull(2, java.sql.Types.VARCHAR);
                statement.setString(3, guildId);
                if (actorUuid == null) {
                    statement.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(4, actorUuid.toString());
                }
                statement.setString(5, operation.trim());
                if (tabId == null || tabId.isBlank()) {
                    statement.setNull(6, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(6, tabId.trim());
                }
                if (slotIndex == null) {
                    statement.setNull(7, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(7, slotIndex);
                }
                if (fingerprint == null || fingerprint.isBlank()) {
                    statement.setNull(8, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(8, fingerprint.trim());
                }
                if (facilityId == null || facilityId.isBlank()) {
                    statement.setNull(9, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(9, facilityId.trim());
                }
                statement.setString(10, Instant.now().toString());
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
            String facilityId,
            UUID operationId) {
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
        Objects.requireNonNull(operationId, "operationId");
        if (simulateIndeterminateCommitForTests) {
            simulateIndeterminateCommitForTests = false;
            return new DepositAuditOutcome(SlotMutationResult.UNKNOWN, null);
        }
        DatabaseManager.TransactionExecutionResult<DepositAuditOutcome> outcome =
                databaseManager.executeTransactionWithDetailedOutcome(connection -> {
                    Long currentVersion = selectSlotVersion(connection, guildId, tabId, slotIndex);
                    if (currentVersion != null) {
                        return new DepositAuditOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    Instant now = Instant.now();
                    insertSlot(connection, guildId, tabId, slotIndex, item, 1L, now);
                    insertAudit(
                            connection,
                            operationId,
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
        return switch (outcome.outcome()) {
            case COMMITTED -> outcome.value();
            case ROLLED_BACK -> new DepositAuditOutcome(SlotMutationResult.FAILED, null);
            case INDETERMINATE -> new DepositAuditOutcome(SlotMutationResult.UNKNOWN, null);
        };
    }

    public WithdrawAuditOutcome withdrawWithAudit(
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload expectedItem,
            long expectedVersion,
            UUID actorUuid,
            String facilityId,
            UUID operationId) {
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
        Objects.requireNonNull(operationId, "operationId");
        if (simulateIndeterminateCommitForTests) {
            simulateIndeterminateCommitForTests = false;
            return new WithdrawAuditOutcome(SlotMutationResult.UNKNOWN, null);
        }
        DatabaseManager.TransactionExecutionResult<WithdrawAuditOutcome> outcome =
                databaseManager.executeTransactionWithDetailedOutcome(connection -> {
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
                    Instant now = Instant.now();
                    insertAudit(
                            connection,
                            operationId,
                            guildId,
                            actorUuid,
                            "WITHDRAW",
                            tabId,
                            slotIndex,
                            expectedItem.fingerprint(),
                            facilityId.trim());
                    insertPayoutObligation(
                            connection,
                            operationId,
                            guildId,
                            actorUuid,
                            tabId,
                            slotIndex,
                            expectedItem,
                            facilityId.trim(),
                            now);
                    return new WithdrawAuditOutcome(SlotMutationResult.SUCCESS, expectedItem);
                });
        return switch (outcome.outcome()) {
            case COMMITTED -> outcome.value();
            case ROLLED_BACK -> new WithdrawAuditOutcome(SlotMutationResult.FAILED, null);
            case INDETERMINATE -> new WithdrawAuditOutcome(SlotMutationResult.UNKNOWN, null);
        };
    }


    public Optional<StoragePayoutObligationRecord> findPayoutObligation(UUID withdrawOperationId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        try (Connection connection = databaseManager.getConnection()) {
            return selectPayoutObligation(connection, withdrawOperationId.toString());
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load payout obligation for withdraw " + withdrawOperationId, e);
        }
    }

    public List<StoragePayoutObligationRecord> findPendingPayoutObligations() {
        return findPayoutObligationsByStatus(StoragePayoutObligationStatus.PENDING);
    }

    public List<StoragePayoutObligationRecord> findOutstandingPayoutObligations() {
        List<StoragePayoutObligationRecord> outstanding = new ArrayList<>();
        outstanding.addAll(findPayoutObligationsByStatus(StoragePayoutObligationStatus.PENDING));
        outstanding.addAll(findPayoutObligationsByStatus(StoragePayoutObligationStatus.DELIVERING));
        outstanding.addAll(findPayoutObligationsByStatus(StoragePayoutObligationStatus.UNKNOWN));
        return List.copyOf(outstanding);
    }

    public Optional<UUID> claimPayoutObligationForDelivery(UUID withdrawOperationId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        UUID deliveryToken = UUID.randomUUID();
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection ->
                beginPayoutDelivery(connection, withdrawOperationId.toString(), deliveryToken, now));
        if (updated.orElse(false)) {
            return Optional.of(deliveryToken);
        }
        return findPayoutObligation(withdrawOperationId)
                .filter(obligation -> obligation.status() == StoragePayoutObligationStatus.DELIVERING
                        && obligation.deliveryToken() != null)
                .map(StoragePayoutObligationRecord::deliveryToken);
    }

    public boolean releasePayoutObligationDeliveryClaim(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection ->
                transitionPayoutDelivery(
                        connection,
                        withdrawOperationId.toString(),
                        deliveryToken,
                        StoragePayoutObligationStatus.DELIVERING,
                        StoragePayoutObligationStatus.PENDING,
                        null,
                        now));
        return updated.orElse(false);
    }

    public boolean markPayoutObligationDelivered(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        Optional<StoragePayoutObligationRecord> obligation = findPayoutObligation(withdrawOperationId);
        if (obligation.isPresent() && obligation.get().status() == StoragePayoutObligationStatus.DELIVERED) {
            return true;
        }
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection ->
                transitionPayoutDelivery(
                        connection,
                        withdrawOperationId.toString(),
                        deliveryToken,
                        StoragePayoutObligationStatus.DELIVERING,
                        StoragePayoutObligationStatus.DELIVERED,
                        null,
                        now));
        return updated.orElse(false);
    }

    public boolean markPayoutObligationDeliveryUnknown(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection ->
                transitionPayoutDelivery(
                        connection,
                        withdrawOperationId.toString(),
                        deliveryToken,
                        StoragePayoutObligationStatus.DELIVERING,
                        StoragePayoutObligationStatus.UNKNOWN,
                        deliveryToken,
                        now));
        return updated.orElse(false);
    }

    public boolean cancelPayoutDeliveryForReinsertion(UUID withdrawOperationId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection -> {
            Optional<StoragePayoutObligationRecord> obligation =
                    selectPayoutObligation(connection, withdrawOperationId.toString());
            if (obligation.isEmpty()) {
                return false;
            }
            return switch (obligation.get().status()) {
                case PENDING -> true;
                case DELIVERING -> transitionPayoutDelivery(
                        connection,
                        withdrawOperationId.toString(),
                        obligation.get().deliveryToken(),
                        StoragePayoutObligationStatus.DELIVERING,
                        StoragePayoutObligationStatus.PENDING,
                        null,
                        now);
                default -> false;
            };
        });
        return updated.orElse(false);
    }

    public boolean assignPayoutReinsertAttempt(UUID withdrawOperationId, UUID reinsertOperationId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(reinsertOperationId, "reinsertOperationId");
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE guild_storage_payout_obligations
                    SET reinsert_operation_id = ?, updated_at = ?
                    WHERE withdraw_operation_id = ?
                      AND status IN (?, ?)
                    """)) {
                statement.setString(1, reinsertOperationId.toString());
                statement.setString(2, now.toString());
                statement.setString(3, withdrawOperationId.toString());
                statement.setString(4, StoragePayoutObligationStatus.PENDING.name());
                statement.setString(5, StoragePayoutObligationStatus.UNKNOWN.name());
                return statement.executeUpdate() == 1;
            }
        });
        return updated.orElse(false);
    }

    public void insertDepositRestorationObligation(
            UUID depositOperationId,
            String guildId,
            UUID actorUuid,
            String tabId,
            int slotIndex,
            String facilityId,
            OpaqueItemPayload item) {
        Objects.requireNonNull(depositOperationId, "depositOperationId");
        requireGuildId(guildId);
        Objects.requireNonNull(actorUuid, "actorUuid");
        requireTabId(tabId);
        Objects.requireNonNull(item, "item");
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId is required");
        }
        Instant now = Instant.now();
        databaseManager.executeTransaction(connection -> insertDepositRestorationObligation(
                connection,
                depositOperationId,
                guildId,
                actorUuid,
                tabId,
                slotIndex,
                facilityId.trim(),
                item,
                now));
    }

    public List<StorageDepositRestorationRecord> findPendingDepositRestorations() {
        return findDepositRestorationsByStatus(StorageDepositRestorationStatus.PENDING);
    }

    public boolean markDepositRestorationComplete(UUID depositOperationId) {
        Objects.requireNonNull(depositOperationId, "depositOperationId");
        Instant now = Instant.now();
        Optional<Boolean> updated = databaseManager.executeTransactionWithResult(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE guild_storage_deposit_restoration_obligations
                    SET status = ?, updated_at = ?
                    WHERE deposit_operation_id = ?
                      AND status IN (?, ?)
                    """)) {
                statement.setString(1, StorageDepositRestorationStatus.RESTORED.name());
                statement.setString(2, now.toString());
                statement.setString(3, depositOperationId.toString());
                statement.setString(4, StorageDepositRestorationStatus.PENDING.name());
                statement.setString(5, StorageDepositRestorationStatus.RESTORING.name());
                return statement.executeUpdate() == 1;
            }
        });
        return updated.orElse(false);
    }

    public ReinsertWithdrawPayoutOutcome reinsertWithdrawPayoutWithAudit(
            UUID withdrawOperationId,
            UUID reinsertOperationId,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            UUID actorUuid,
            String facilityId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(reinsertOperationId, "reinsertOperationId");
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
        if (simulateIndeterminateCommitForTests) {
            simulateIndeterminateCommitForTests = false;
            return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.UNKNOWN, null);
        }
        DatabaseManager.TransactionExecutionResult<ReinsertWithdrawPayoutOutcome> outcome =
                databaseManager.executeTransactionWithDetailedOutcome(connection -> {
                    Optional<StoragePayoutObligationRecord> obligation =
                            selectPayoutObligation(connection, withdrawOperationId.toString());
                    if (obligation.isEmpty()) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    StoragePayoutObligationRecord current = obligation.get();
                    if (current.status() == StoragePayoutObligationStatus.DELIVERED) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    if (current.status() == StoragePayoutObligationStatus.DELIVERING) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    if (current.status() == StoragePayoutObligationStatus.REINSERTED) {
                        if (current.reinsertOperationId() == null) {
                            return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                        }
                        Optional<StorageOperationRecord> reinsertOperation =
                                selectOperation(connection, current.reinsertOperationId().toString());
                        if (reinsertOperation.isEmpty()
                                || !"PAYOUT_REINSERT".equals(reinsertOperation.get().operationType())
                                || reinsertOperation.get().status() != StorageOperationStatus.COMMITTED) {
                            return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                        }
                        Optional<StorageSlot> existing = selectSlot(connection, guildId, tabId, slotIndex);
                        if (existing.isPresent() && existing.get().item().equals(item)) {
                            return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.SUCCESS, existing.get());
                        }
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    if (!current.item().equals(item)
                            || current.slotIndex() != slotIndex
                            || !current.tabId().equals(tabId)
                            || !current.guildId().equals(guildId)
                            || !current.facilityId().equals(facilityId.trim())
                            || !current.actorUuid().equals(actorUuid)) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    Long currentVersion = selectSlotVersion(connection, guildId, tabId, slotIndex);
                    if (currentVersion != null) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    Instant now = Instant.now();
                    insertSlot(connection, guildId, tabId, slotIndex, item, 1L, now);
                    insertAudit(
                            connection,
                            reinsertOperationId,
                            guildId,
                            actorUuid,
                            "PAYOUT_REINSERT",
                            tabId,
                            slotIndex,
                            item.fingerprint(),
                            facilityId.trim());
                    if (!completePayoutReinsert(
                            connection,
                            withdrawOperationId.toString(),
                            reinsertOperationId.toString(),
                            now)) {
                        return new ReinsertWithdrawPayoutOutcome(SlotMutationResult.CONFLICT, null);
                    }
                    return new ReinsertWithdrawPayoutOutcome(
                            SlotMutationResult.SUCCESS,
                            new StorageSlot(guildId, tabId, slotIndex, item, 1L, now));
                });
        return switch (outcome.outcome()) {
            case COMMITTED -> outcome.value();
            case ROLLED_BACK -> new ReinsertWithdrawPayoutOutcome(SlotMutationResult.FAILED, null);
            case INDETERMINATE -> new ReinsertWithdrawPayoutOutcome(SlotMutationResult.UNKNOWN, null);
        };
    }

    public StorageOperationLookupResult lookupOperation(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = databaseManager.getConnection()) {
            return selectOperation(connection, operationId.toString())
                    .map(StorageOperationLookupResult::found)
                    .orElseGet(StorageOperationLookupResult::notFound);
        } catch (SQLException e) {
            return StorageOperationLookupResult.readFailure(
                    "Failed to load storage operation " + operationId + ": " + e.getMessage());
        }
    }

    public Optional<StorageOperationRecord> findOperation(UUID operationId) {
        StorageOperationLookupResult lookup = lookupOperation(operationId);
        return switch (lookup.status()) {
            case FOUND -> lookup.record();
            case NOT_FOUND -> Optional.empty();
            case READ_FAILURE -> throw new IllegalStateException(lookup.errorMessage());
        };
    }

    public List<StorageOperationRecord> findPendingOperations() {
        return findOperationsByStatus(StorageOperationStatus.PENDING);
    }
    public List<StorageOperationRecord> findUnknownOperations() {
        return findOperationsByStatus(StorageOperationStatus.UNKNOWN);
    }


    /**
     * Returns whether durable audit evidence exists for a storage mutation at the given slot.
     * Used to reconcile interrupted operation journal rows against committed slot/audit state.
     */
    public AuditEvidenceLookupResult lookupMatchingAudit(
            UUID operationId,
            String guildId,
            UUID actorUuid,
            String operation,
            String tabId,
            int slotIndex,
            String facilityId,
            Instant notBefore,
            OpaqueItemPayload expectedSnapshot) {
        Objects.requireNonNull(operationId, "operationId");
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
        Objects.requireNonNull(expectedSnapshot, "expectedSnapshot");
        try (Connection connection = databaseManager.getConnection()) {
            boolean mysql = SqlSupport.mysql(connection);
            String recordedAtPredicate = SqlSupport.instantTextAtOrAfter(mysql, "recorded_at");
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1
                    FROM guild_storage_audit
                    WHERE operation_id = ?
                      AND guild_id = ?
                      AND actor_uuid = ?
                      AND operation = ?
                      AND tab_id = ?
                      AND slot_index = ?
                      AND facility_id = ?
                      AND fingerprint = ?
                      AND """ + recordedAtPredicate + """
                    LIMIT 1
                    """)) {
                statement.setString(1, operationId.toString());
                statement.setString(2, guildId.trim());
                statement.setString(3, actorUuid.toString());
                statement.setString(4, operation.trim());
                statement.setString(5, tabId.trim());
                statement.setInt(6, slotIndex);
                statement.setString(7, facilityId.trim());
                statement.setString(8, expectedSnapshot.fingerprint());
                SqlSupport.bindInstantAtOrAfter(statement, 9, notBefore);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? AuditEvidenceLookupResult.matching() : AuditEvidenceLookupResult.none();
                }
            }
        } catch (SQLException e) {
            return AuditEvidenceLookupResult.readFailure(
                    "Failed to query storage audit evidence for guild " + guildId + ": " + e.getMessage());
        }
    }

    public boolean hasMatchingAudit(
            UUID operationId,
            String guildId,
            UUID actorUuid,
            String operation,
            String tabId,
            int slotIndex,
            String facilityId,
            Instant notBefore,
            OpaqueItemPayload expectedSnapshot) {
        Objects.requireNonNull(operationId, "operationId");
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
        Objects.requireNonNull(expectedSnapshot, "expectedSnapshot");
        AuditEvidenceLookupResult lookup = lookupMatchingAudit(
                operationId,
                guildId,
                actorUuid,
                operation,
                tabId,
                slotIndex,
                facilityId,
                notBefore,
                expectedSnapshot);
        return switch (lookup.status()) {
            case MATCHING -> true;
            case NONE -> false;
            case READ_FAILURE -> throw new IllegalStateException(lookup.errorMessage());
        };
    }

    public boolean insertPendingOperation(
            UUID operationId,
            String guildId,
            String operationType,
            UUID actorUuid,
            String tabId,
            int slotIndex,
            String facilityId,
            OpaqueItemPayload requestSnapshot) {
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
                    requestSnapshot,
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
        DatabaseManager.TransactionExecutionResult<Boolean> outcome =
                databaseManager.executeTransactionWithDetailedOutcome(connection ->
                        updateOperationResult(
                                connection,
                                operationId.toString(),
                                status.name(),
                                resultStatus,
                                resultError,
                                resultSlot,
                                resultItem,
                                now));
        if (outcome.outcome() != DatabaseManager.TransactionCommitOutcome.COMMITTED
                || !Boolean.TRUE.equals(outcome.value())) {
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
                            request_item_schema, request_item_fingerprint, request_item_payload,
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
                       request_item_schema, request_item_fingerprint, request_item_payload,
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
            OpaqueItemPayload requestSnapshot,
            Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_operations (
                    operation_id, guild_id, operation_type, actor_uuid, tab_id, slot_index,
                    facility_id, status,
                    request_item_schema, request_item_fingerprint, request_item_payload,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, operationId);
            statement.setString(2, guildId);
            statement.setString(3, operationType);
            statement.setString(4, actorUuid);
            statement.setString(5, tabId);
            statement.setInt(6, slotIndex);
            statement.setString(7, facilityId);
            statement.setString(8, StorageOperationStatus.PENDING.name());
            if (requestSnapshot == null) {
                statement.setNull(9, java.sql.Types.VARCHAR);
                statement.setNull(10, java.sql.Types.VARCHAR);
                statement.setNull(11, java.sql.Types.VARCHAR);
            } else {
                statement.setString(9, requestSnapshot.schema());
                statement.setString(10, requestSnapshot.fingerprint());
                statement.setString(11, requestSnapshot.payload());
            }
            statement.setString(12, timestamp.toString());
            statement.setString(13, timestamp.toString());
            statement.executeUpdate();
        }
    }

    private static boolean updateOperationResult(
            Connection connection,
            String operationId,
            String status,
            String resultStatus,
            String resultError,
            StorageSlot resultSlot,
            OpaqueItemPayload resultItem,
            Instant updatedAt) throws SQLException {
        OpaqueItemPayload resultPayload = resultItem;
        if (resultPayload == null && resultSlot != null) {
            resultPayload = resultSlot.item();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_operations
                SET status = ?, result_status = ?, result_error = ?,
                    result_item_schema = ?, result_item_fingerprint = ?, result_item_payload = ?,
                    result_slot_version = ?, result_slot_updated_at = ?, updated_at = ?
                WHERE operation_id = ?
                  AND (status = ? OR status = ? OR status = ?)
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
            if (resultPayload == null) {
                statement.setNull(4, java.sql.Types.VARCHAR);
                statement.setNull(5, java.sql.Types.VARCHAR);
                statement.setNull(6, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, resultPayload.schema());
                statement.setString(5, resultPayload.fingerprint());
                statement.setString(6, resultPayload.payload());
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
            statement.setString(11, StorageOperationStatus.PENDING.name());
            statement.setString(12, StorageOperationStatus.UNKNOWN.name());
            statement.setString(13, status);
            if (statement.executeUpdate() > 0) {
                return true;
            }
        }
        return preservesTerminalJournalState(connection, operationId, status);
    }

    private static boolean preservesTerminalJournalState(
            Connection connection, String operationId, String targetStatus) throws SQLException {
        Optional<StorageOperationRecord> current = selectOperation(connection, operationId);
        if (current.isEmpty()) {
            return false;
        }
        String currentStatus = current.get().status().name();
        if (currentStatus.equals(targetStatus)) {
            return true;
        }
        return isPreservedTerminalStatus(currentStatus)
                && StorageOperationStatus.UNKNOWN.name().equals(targetStatus);
    }

    private static boolean isPreservedTerminalStatus(String status) {
        return StorageOperationStatus.COMMITTED.name().equals(status)
                || StorageOperationStatus.COMPENSATED.name().equals(status);
    }

    private static StorageOperationRecord mapOperation(ResultSet result) throws SQLException {
        OpaqueItemPayload requestSnapshot = readItemPayload(
                result, "request_item_schema", "request_item_fingerprint", "request_item_payload");
        OpaqueItemPayload resultItem = readItemPayload(
                result, "result_item_schema", "result_item_fingerprint", "result_item_payload");
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
                requestSnapshot,
                StorageOperationStatus.valueOf(result.getString("status")),
                result.getString("result_status"),
                result.getString("result_error"),
                resultItem,
                resultSlot,
                parseInstant(result.getString("created_at")),
                parseInstant(result.getString("updated_at")));
    }

    private static OpaqueItemPayload readItemPayload(
            ResultSet result, String schemaColumn, String fingerprintColumn, String payloadColumn)
            throws SQLException {
        String schema = result.getString(schemaColumn);
        if (schema == null || schema.isBlank()) {
            return null;
        }
        return new OpaqueItemPayload(schema, result.getString(fingerprintColumn), result.getString(payloadColumn));
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
            UUID operationId,
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
                    id, operation_id, guild_id, actor_uuid, operation, tab_id, slot_index, fingerprint, facility_id, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, operationId.toString());
            statement.setString(3, guildId);
            statement.setString(4, actorUuid.toString());
            statement.setString(5, operation.trim());
            statement.setString(6, tabId.trim());
            statement.setInt(7, slotIndex);
            if (fingerprint == null || fingerprint.isBlank()) {
                statement.setNull(8, java.sql.Types.VARCHAR);
            } else {
                statement.setString(8, fingerprint.trim());
            }
            statement.setString(9, facilityId.trim());
            statement.setString(10, Instant.now().toString());
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


    private List<StoragePayoutObligationRecord> findPayoutObligationsByStatus(StoragePayoutObligationStatus status) {
        Objects.requireNonNull(status, "status");
        List<StoragePayoutObligationRecord> obligations = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT withdraw_operation_id, guild_id, actor_uuid, tab_id, slot_index, facility_id,
                            item_schema, item_fingerprint, item_payload, status, reinsert_operation_id,
                            delivery_token, created_at, updated_at
                     FROM guild_storage_payout_obligations
                     WHERE status = ?
                     ORDER BY created_at
                     """)) {
            statement.setString(1, status.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    obligations.add(mapPayoutObligation(result));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load payout obligations in status " + status, e);
        }
        return List.copyOf(obligations);
    }

    private static Optional<StoragePayoutObligationRecord> selectPayoutObligation(
            Connection connection, String withdrawOperationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT withdraw_operation_id, guild_id, actor_uuid, tab_id, slot_index, facility_id,
                       item_schema, item_fingerprint, item_payload, status, reinsert_operation_id,
                       delivery_token, created_at, updated_at
                FROM guild_storage_payout_obligations
                WHERE withdraw_operation_id = ?
                """)) {
            statement.setString(1, withdrawOperationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapPayoutObligation(result));
            }
        }
    }

    private static StoragePayoutObligationRecord mapPayoutObligation(ResultSet result) throws SQLException {
        OpaqueItemPayload item = new OpaqueItemPayload(
                result.getString("item_schema"),
                result.getString("item_fingerprint"),
                result.getString("item_payload"));
        String reinsertOperationId = result.getString("reinsert_operation_id");
        String deliveryToken = result.getString("delivery_token");
        return new StoragePayoutObligationRecord(
                UUID.fromString(result.getString("withdraw_operation_id")),
                result.getString("guild_id"),
                UUID.fromString(result.getString("actor_uuid")),
                result.getString("tab_id"),
                result.getInt("slot_index"),
                result.getString("facility_id"),
                item,
                StoragePayoutObligationStatus.valueOf(result.getString("status")),
                reinsertOperationId == null || reinsertOperationId.isBlank()
                        ? null
                        : UUID.fromString(reinsertOperationId),
                deliveryToken == null || deliveryToken.isBlank() ? null : UUID.fromString(deliveryToken),
                parseInstant(result.getString("created_at")),
                parseInstant(result.getString("updated_at")));
    }

    private static void insertPayoutObligation(
            Connection connection,
            UUID withdrawOperationId,
            String guildId,
            UUID actorUuid,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId,
            Instant timestamp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_payout_obligations (
                    withdraw_operation_id, guild_id, actor_uuid, tab_id, slot_index, facility_id,
                    item_schema, item_fingerprint, item_payload, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, withdrawOperationId.toString());
            statement.setString(2, guildId);
            statement.setString(3, actorUuid.toString());
            statement.setString(4, tabId);
            statement.setInt(5, slotIndex);
            statement.setString(6, facilityId);
            statement.setString(7, item.schema());
            statement.setString(8, item.fingerprint());
            statement.setString(9, item.payload());
            statement.setString(10, StoragePayoutObligationStatus.PENDING.name());
            statement.setString(11, timestamp.toString());
            statement.setString(12, timestamp.toString());
            statement.executeUpdate();
        }
    }

    private static boolean beginPayoutDelivery(
            Connection connection,
            String withdrawOperationId,
            UUID deliveryToken,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_payout_obligations
                SET status = ?, delivery_token = ?, updated_at = ?
                WHERE withdraw_operation_id = ? AND status = ?
                """)) {
            statement.setString(1, StoragePayoutObligationStatus.DELIVERING.name());
            statement.setString(2, deliveryToken.toString());
            statement.setString(3, updatedAt.toString());
            statement.setString(4, withdrawOperationId);
            statement.setString(5, StoragePayoutObligationStatus.PENDING.name());
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean transitionPayoutDelivery(
            Connection connection,
            String withdrawOperationId,
            UUID deliveryToken,
            StoragePayoutObligationStatus expectedStatus,
            StoragePayoutObligationStatus nextStatus,
            UUID nextDeliveryToken,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_payout_obligations
                SET status = ?, delivery_token = ?, updated_at = ?
                WHERE withdraw_operation_id = ? AND status = ?
                  AND (delivery_token = ? OR (? IS NULL AND delivery_token IS NULL))
                """)) {
            statement.setString(1, nextStatus.name());
            if (nextDeliveryToken == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, nextDeliveryToken.toString());
            }
            statement.setString(3, updatedAt.toString());
            statement.setString(4, withdrawOperationId);
            statement.setString(5, expectedStatus.name());
            if (deliveryToken == null) {
                statement.setNull(6, java.sql.Types.VARCHAR);
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(6, deliveryToken.toString());
                statement.setString(7, deliveryToken.toString());
            }
            return statement.executeUpdate() == 1;
        }
    }

    private static boolean updatePayoutObligationStatus(
            Connection connection,
            String withdrawOperationId,
            StoragePayoutObligationStatus expectedStatus,
            StoragePayoutObligationStatus nextStatus,
            String reinsertOperationId,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guild_storage_payout_obligations
                SET status = ?, reinsert_operation_id = ?, delivery_token = NULL, updated_at = ?
                WHERE withdraw_operation_id = ? AND status = ?
                """)) {
            statement.setString(1, nextStatus.name());
            if (reinsertOperationId == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(2, reinsertOperationId);
            }
            statement.setString(3, updatedAt.toString());
            statement.setString(4, withdrawOperationId);
            statement.setString(5, expectedStatus.name());
            return statement.executeUpdate() == 1;
        }
    }


    private static boolean completePayoutReinsert(
            Connection connection,
            String withdrawOperationId,
            String reinsertOperationId,
            Instant updatedAt) throws SQLException {
        if (updatePayoutObligationStatus(
                connection,
                withdrawOperationId,
                StoragePayoutObligationStatus.PENDING,
                StoragePayoutObligationStatus.REINSERTED,
                reinsertOperationId,
                updatedAt)) {
            return true;
        }
        return updatePayoutObligationStatus(
                connection,
                withdrawOperationId,
                StoragePayoutObligationStatus.UNKNOWN,
                StoragePayoutObligationStatus.REINSERTED,
                reinsertOperationId,
                updatedAt);
    }

    private List<StorageDepositRestorationRecord> findDepositRestorationsByStatus(
            StorageDepositRestorationStatus status) {
        Objects.requireNonNull(status, "status");
        List<StorageDepositRestorationRecord> obligations = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT deposit_operation_id, guild_id, actor_uuid, tab_id, slot_index, facility_id,
                            item_schema, item_fingerprint, item_payload, status, created_at, updated_at
                     FROM guild_storage_deposit_restoration_obligations
                     WHERE status = ?
                     ORDER BY created_at
                     """)) {
            statement.setString(1, status.name());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    obligations.add(mapDepositRestoration(result));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load deposit restoration obligations in status " + status, e);
        }
        return List.copyOf(obligations);
    }

    private static StorageDepositRestorationRecord mapDepositRestoration(ResultSet result) throws SQLException {
        OpaqueItemPayload item = new OpaqueItemPayload(
                result.getString("item_schema"),
                result.getString("item_fingerprint"),
                result.getString("item_payload"));
        return new StorageDepositRestorationRecord(
                UUID.fromString(result.getString("deposit_operation_id")),
                result.getString("guild_id"),
                UUID.fromString(result.getString("actor_uuid")),
                result.getString("tab_id"),
                result.getInt("slot_index"),
                result.getString("facility_id"),
                item,
                StorageDepositRestorationStatus.valueOf(result.getString("status")),
                parseInstant(result.getString("created_at")),
                parseInstant(result.getString("updated_at")));
    }

    private static void insertDepositRestorationObligation(
            Connection connection,
            UUID depositOperationId,
            String guildId,
            UUID actorUuid,
            String tabId,
            int slotIndex,
            String facilityId,
            OpaqueItemPayload item,
            Instant timestamp) throws SQLException {
        try (PreparedStatement existing = connection.prepareStatement("""
                SELECT 1
                FROM guild_storage_deposit_restoration_obligations
                WHERE deposit_operation_id = ?
                """)) {
            existing.setString(1, depositOperationId.toString());
            try (ResultSet result = existing.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO guild_storage_deposit_restoration_obligations (
                    deposit_operation_id, guild_id, actor_uuid, tab_id, slot_index, facility_id,
                    item_schema, item_fingerprint, item_payload, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, depositOperationId.toString());
            statement.setString(2, guildId);
            statement.setString(3, actorUuid.toString());
            statement.setString(4, tabId);
            statement.setInt(5, slotIndex);
            statement.setString(6, facilityId);
            statement.setString(7, item.schema());
            statement.setString(8, item.fingerprint());
            statement.setString(9, item.payload());
            statement.setString(10, StorageDepositRestorationStatus.PENDING.name());
            statement.setString(11, timestamp.toString());
            statement.setString(12, timestamp.toString());
            statement.executeUpdate();
        }
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
