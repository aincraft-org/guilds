package org.aincraft.guilds.storage.service.impl;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.persist.StorageOperationRecord;
import org.aincraft.guilds.storage.persist.StorageOperationStatus;
import org.aincraft.guilds.storage.service.GuildStorageService;
import org.aincraft.guilds.storage.service.MainThreadExecutor;
import org.aincraft.guilds.storage.service.StorageFacilityAccessValidator;
import org.aincraft.guilds.storage.service.StorageResult;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class GuildStorageServiceImpl implements GuildStorageService {
    private enum GuildStorageRole {
        OUTSIDER(0),
        MEMBER(1),
        ASSISTANT(2),
        MAYOR(3);

        private final int rank;

        GuildStorageRole(int rank) {
            this.rank = rank;
        }

        static GuildStorageRole fromGuild(Guild guild, UUID actor) {
            if (guild.isMayor(actor)) {
                return MAYOR;
            }
            if (guild.isAssistant(actor)) {
                return ASSISTANT;
            }
            if (guild.isResident(actor)) {
                return MEMBER;
            }
            return OUTSIDER;
        }

        static GuildStorageRole parse(String roleName) {
            if (roleName == null || roleName.isBlank()) {
                throw new IllegalArgumentException("role is required");
            }
            return GuildStorageRole.valueOf(roleName.trim().toUpperCase(java.util.Locale.ROOT));
        }

        boolean satisfies(GuildStorageRole required) {
            return rank >= required.rank;
        }
    }

    private static final class DirectExecutor implements MainThreadExecutor, Executor {
        private static final DirectExecutor INSTANCE = new DirectExecutor();

        @Override
        public void run(Runnable task) {
            task.run();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private final SqlGuildStorageStore store;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final StorageFacilityAccessValidator facilityAccess;
    private final MainThreadExecutor mainThreadExecutor;
    private final Executor sqlExecutor;

    public GuildStorageServiceImpl(
            SqlGuildStorageStore store,
            GuildService guildService,
            ResidentService residentService,
            StorageFacilityAccessValidator facilityAccess,
            MainThreadExecutor mainThreadExecutor,
            Executor sqlExecutor) {
        this.store = Objects.requireNonNull(store, "store");
        this.guildService = Objects.requireNonNull(guildService, "guildService");
        this.residentService = Objects.requireNonNull(residentService, "residentService");
        this.facilityAccess = Objects.requireNonNull(facilityAccess, "facilityAccess");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
        reconcilePendingOperations();
    }

    public static GuildStorageServiceImpl withDirectExecutorsForUnitTests(
            SqlGuildStorageStore store,
            GuildService guildService,
            ResidentService residentService,
            StorageFacilityAccessValidator facilityAccess) {
        return new GuildStorageServiceImpl(
                store, guildService, residentService, facilityAccess, DirectExecutor.INSTANCE, DirectExecutor.INSTANCE);
    }

    @Override
    public StorageResult<GuildStorageBank> getBank(UUID actor, String guildId) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        try {
            return StorageResult.success(store.getOrCreateBank(requireGuildId(guildId)));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    @Override
    public StorageResult<List<StorageTab>> getTabs(UUID actor, String guildId) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            return StorageResult.success(store.loadTabs(guildId));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    @Override
    public StorageResult<Map<Integer, StorageSlot>> getSlots(UUID actor, String guildId, String tabId) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        StorageResult<StorageTab> tab = resolveTab(guildId, tabId);
        if (!tab.isSuccess()) {
            return mapFailure(tab);
        }
        try {
            return StorageResult.success(store.loadSlots(guildId, tab.value().orElseThrow().tabId()));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    @Override
    public StorageResult<StorageSlot> deposit(
            UUID operationId,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId) {
        return depositWithCompensation(
                actor, guildId, tabId, slotIndex, item, facilityId, operationId, null);
    }

    public StorageResult<StorageSlot> depositWithCompensation(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId,
            UUID operationId,
            Runnable compensationOnFailure) {
        StorageResult<PreparedDeposit> prepared =
                prepareDeposit(actor, guildId, tabId, slotIndex, item, facilityId, operationId);
        if (!prepared.isSuccess()) {
            return mapFailure(prepared);
        }
        PreparedDeposit deposit = prepared.value().orElseThrow();
        return executeMutation(
                operationId,
                "DEPOSIT",
                deposit.actor(),
                deposit.guildId(),
                deposit.tabId(),
                deposit.slotIndex(),
                deposit.facilityId(),
                compensationOnFailure,
                () -> persistDeposit(deposit));
    }

    @Override
    public StorageResult<OpaqueItemPayload> withdraw(
            UUID operationId, UUID actor, String guildId, String tabId, int slotIndex, String facilityId) {
        return withdrawWithCompensation(
                actor, guildId, tabId, slotIndex, facilityId, operationId, null);
    }

    public StorageResult<OpaqueItemPayload> withdrawWithCompensation(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            UUID operationId,
            Runnable compensationOnFailure) {
        StorageResult<PreparedWithdraw> prepared =
                prepareWithdraw(actor, guildId, tabId, slotIndex, facilityId, operationId);
        if (!prepared.isSuccess()) {
            return mapFailure(prepared);
        }
        PreparedWithdraw withdraw = prepared.value().orElseThrow();
        return executeMutation(
                operationId,
                "WITHDRAW",
                withdraw.actor(),
                withdraw.guildId(),
                withdraw.tabId(),
                withdraw.slotIndex(),
                withdraw.facilityId(),
                compensationOnFailure,
                () -> persistWithdraw(withdraw));
    }

    @Override
    public StorageResult<StoragePolicy> getPolicy(UUID actor, String guildId) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            return loadPolicySafely(guildId);
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    @Override
    public StorageResult<StoragePolicy> updatePolicy(
            UUID actor, String guildId, String depositRole, String withdrawRole, String manageRole) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        StorageResult<StoragePolicy> policy = loadPolicySafely(guildId);
        if (!policy.isSuccess()) {
            return mapFailure(policy);
        }
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(),
                GuildStorageRole.parse(policy.value().orElseThrow().manageRole()));
        if (!permission.isSuccess()) {
            return mapFailure(permission);
        }
        try {
            GuildStorageRole.parse(depositRole);
            GuildStorageRole.parse(withdrawRole);
            GuildStorageRole.parse(manageRole);
        } catch (IllegalArgumentException e) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, e.getMessage());
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            StoragePolicy updated =
                    new StoragePolicy(guildId, depositRole, withdrawRole, manageRole, Instant.now());
            store.savePolicy(updated);
            return StorageResult.success(updated);
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private void reconcilePendingOperations() {
        store.findPendingOperations();
    }

    private StorageResult<PreparedDeposit> prepareDeposit(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId,
            UUID operationId) {
        if (operationId == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "operationId is required");
        }
        if (item == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "item is required");
        }
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        StorageResult<Void> facility = facilityAccess.validateMutationAccess(actor, guildId, facilityId);
        if (!facility.isSuccess()) {
            return mapFailure(facility);
        }
        StorageResult<StoragePolicy> policy = loadPolicySafely(guildId);
        if (!policy.isSuccess()) {
            return mapFailure(policy);
        }
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(),
                GuildStorageRole.parse(policy.value().orElseThrow().depositRole()));
        if (!permission.isSuccess()) {
            return mapFailure(permission);
        }
        StorageResult<StorageTab> tab = resolveTab(guildId, tabId);
        if (!tab.isSuccess()) {
            return mapFailure(tab);
        }
        StorageResult<Void> bounds = validateSlotIndex(tab.value().orElseThrow(), slotIndex);
        if (!bounds.isSuccess()) {
            return mapFailure(bounds);
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            if (store.loadSlots(guildId, tabId).containsKey(slotIndex)) {
                return StorageResult.failure(StorageResult.Status.SLOT_OCCUPIED, "Slot already occupied");
            }
            return StorageResult.success(
                    new PreparedDeposit(actor, guildId, tabId, slotIndex, item, facilityId.trim()));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private StorageResult<StorageSlot> persistDeposit(PreparedDeposit prepared) {
        SqlGuildStorageStore.DepositAuditOutcome outcome = store.depositWithAudit(
                prepared.guildId(),
                prepared.tabId(),
                prepared.slotIndex(),
                prepared.item(),
                prepared.actor(),
                prepared.facilityId());
        return switch (outcome.status()) {
            case SUCCESS -> StorageResult.success(outcome.slot());
            case CONFLICT -> StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during deposit");
            case FAILED -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Failed to persist deposit");
        };
    }

    private StorageResult<PreparedWithdraw> prepareWithdraw(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            UUID operationId) {
        if (operationId == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "operationId is required");
        }
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        StorageResult<Void> facility = facilityAccess.validateMutationAccess(actor, guildId, facilityId);
        if (!facility.isSuccess()) {
            return mapFailure(facility);
        }
        StorageResult<StoragePolicy> policy = loadPolicySafely(guildId);
        if (!policy.isSuccess()) {
            return mapFailure(policy);
        }
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(),
                GuildStorageRole.parse(policy.value().orElseThrow().withdrawRole()));
        if (!permission.isSuccess()) {
            return mapFailure(permission);
        }
        StorageResult<StorageTab> tab = resolveTab(guildId, tabId);
        if (!tab.isSuccess()) {
            return mapFailure(tab);
        }
        StorageResult<Void> bounds = validateSlotIndex(tab.value().orElseThrow(), slotIndex);
        if (!bounds.isSuccess()) {
            return mapFailure(bounds);
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            StorageSlot occupied = store.loadSlots(guildId, tabId).get(slotIndex);
            if (occupied == null) {
                return StorageResult.failure(StorageResult.Status.SLOT_EMPTY, "Slot is empty");
            }
            return StorageResult.success(
                    new PreparedWithdraw(actor, guildId, tabId, slotIndex, occupied, facilityId.trim()));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private StorageResult<OpaqueItemPayload> persistWithdraw(PreparedWithdraw prepared) {
        StorageSlot occupied = prepared.occupied();
        SqlGuildStorageStore.WithdrawAuditOutcome outcome = store.withdrawWithAudit(
                prepared.guildId(),
                prepared.tabId(),
                prepared.slotIndex(),
                occupied.item(),
                occupied.version(),
                prepared.actor(),
                prepared.facilityId());
        return switch (outcome.status()) {
            case SUCCESS -> StorageResult.success(outcome.item());
            case CONFLICT -> StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during withdraw");
            case FAILED -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Failed to persist withdraw");
        };
    }

    private <T> StorageResult<T> executeMutation(
            UUID operationId,
            String operationType,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            Runnable compensationOnFailure,
            java.util.function.Supplier<StorageResult<T>> mutation) {
        Optional<StorageOperationRecord> existing = store.findOperation(operationId);
        if (existing.isPresent()) {
            StorageResult<T> replayed = replayOperation(existing.get());
            if (replayed != null) {
                return replayed;
            }
        } else if (!store.insertPendingOperation(
                operationId, guildId, operationType, actor, tabId, slotIndex, facilityId)) {
            existing = store.findOperation(operationId);
            if (existing.isPresent()) {
                StorageResult<T> replayed = replayOperation(existing.get());
                if (replayed != null) {
                    return replayed;
                }
            }
            return StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR, "Failed to record pending storage operation");
        }
        try {
            StorageResult<T> result = CompletableFuture.supplyAsync(mutation, sqlExecutor).get();
            finalizeOperationJournal(operationId, result, compensationOnFailure);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            StorageResult<T> failure =
                    StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Storage mutation interrupted");
            finalizeOperationJournal(operationId, failure, compensationOnFailure);
            return failure;
        } catch (Exception e) {
            StorageResult<T> failure = StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR,
                    e.getCause() instanceof RuntimeException runtime
                            ? runtime.getMessage()
                            : e.getMessage());
            finalizeOperationJournal(operationId, failure, compensationOnFailure);
            return failure;
        }
    }

    private <T> void finalizeOperationJournal(
            UUID operationId, StorageResult<T> result, Runnable compensationOnFailure) {
        if (result.isSuccess()) {
            store.finalizeOperation(
                    operationId,
                    StorageOperationStatus.COMMITTED,
                    result.status().name(),
                    null,
                    resultSlot(result),
                    resultItem(result));
        } else {
            runCompensation(compensationOnFailure);
            store.finalizeOperation(
                    operationId,
                    StorageOperationStatus.COMPENSATED,
                    result.status().name(),
                    result.errorMessage(),
                    null,
                    null);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> StorageSlot resultSlot(StorageResult<T> result) {
        Object value = result.value().orElse(null);
        if (value instanceof StorageSlot slot) {
            return slot;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> OpaqueItemPayload resultItem(StorageResult<T> result) {
        Object value = result.value().orElse(null);
        if (value instanceof OpaqueItemPayload payload) {
            return payload;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> StorageResult<T> replayOperation(StorageOperationRecord operation) {
        if (operation.status() == StorageOperationStatus.PENDING) {
            return null;
        }
        StorageResult.Status status = StorageResult.Status.valueOf(operation.resultStatus());
        if (operation.status() == StorageOperationStatus.COMMITTED && status == StorageResult.Status.SUCCESS) {
            if ("DEPOSIT".equals(operation.operationType()) && operation.resultSlot() != null) {
                return (StorageResult<T>) StorageResult.success(operation.resultSlot());
            }
            if ("WITHDRAW".equals(operation.operationType()) && operation.resultItem() != null) {
                return (StorageResult<T>) StorageResult.success(operation.resultItem());
            }
        }
        return StorageResult.failure(status, operation.resultError());
    }

    private void runCompensation(Runnable compensationOnFailure) {
        if (compensationOnFailure != null) {
            mainThreadExecutor.run(compensationOnFailure);
        }
    }

    private StorageResult<StoragePolicy> loadPolicySafely(String guildId) {
        try {
            return StorageResult.success(store.loadPolicy(guildId));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private StorageResult<AccessContext> resolveAccess(UUID actor, String guildId) {
        if (actor == null) {
            return StorageResult.failure(StorageResult.Status.UNAUTHORIZED, "Actor is required");
        }
        try {
            requireGuildId(guildId);
        } catch (IllegalArgumentException e) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, e.getMessage());
        }
        Optional<Resident> resident = residentService.getResident(actor);
        if (resident.isEmpty()) {
            return StorageResult.failure(StorageResult.Status.UNAUTHORIZED, "Unknown actor");
        }
        Optional<Guild> guild = guildService.getGuildById(guildId);
        if (guild.isEmpty()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "Unknown guild");
        }
        Guild resolvedGuild = guild.get();
        if (!resolvedGuild.getName().equals(resident.get().getGuild()) || !resolvedGuild.isResident(actor)) {
            return StorageResult.failure(StorageResult.Status.UNAUTHORIZED, "Actor is not a guild member");
        }
        return StorageResult.success(new AccessContext(resolvedGuild, GuildStorageRole.fromGuild(resolvedGuild, actor)));
    }

    private StorageResult<Void> requireRole(AccessContext access, GuildStorageRole requiredRole) {
        if (!access.role().satisfies(requiredRole)) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Insufficient storage role");
        }
        return StorageResult.success(null);
    }

    private StorageResult<StorageTab> resolveTab(String guildId, String tabId) {
        if (tabId == null || tabId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "tabId is required");
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            Optional<StorageTab> tab =
                    store.loadTabs(guildId).stream().filter(candidate -> candidate.tabId().equals(tabId)).findFirst();
            return tab.map(StorageResult::success)
                    .orElseGet(() -> StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "Unknown tab"));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private StorageResult<Void> validateSlotIndex(StorageTab tab, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= tab.capacitySlots()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "slotIndex out of bounds");
        }
        if (!tab.unlocked()) {
            return StorageResult.failure(StorageResult.Status.PERMISSION_DENIED, "Tab is locked");
        }
        return StorageResult.success(null);
    }

    private static String requireGuildId(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
        return guildId.trim();
    }

    private static <T> StorageResult<T> mapFailure(StorageResult<?> source) {
        return StorageResult.failure(source.status(), source.errorMessage());
    }

    private record AccessContext(Guild guild, GuildStorageRole role) {}

    private record PreparedDeposit(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId) {}

    private record PreparedWithdraw(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            StorageSlot occupied,
            String facilityId) {}
}
