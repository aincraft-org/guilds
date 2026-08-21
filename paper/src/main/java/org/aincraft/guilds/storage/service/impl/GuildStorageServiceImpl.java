package org.aincraft.guilds.storage.service.impl;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.service.GuildStorageService;
import org.aincraft.guilds.storage.service.MainThreadExecutor;
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
import java.util.concurrent.ConcurrentHashMap;
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

    private final SqlGuildStorageStore store;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final MainThreadExecutor mainThreadExecutor;
    private final Executor sqlExecutor;
    private final ConcurrentHashMap<UUID, StorageResult<?>> completedMutations = new ConcurrentHashMap<>();

    public GuildStorageServiceImpl(
            SqlGuildStorageStore store, GuildService guildService, ResidentService residentService) {
        this(store, guildService, residentService, Runnable::run, ForkJoinPool.commonPool());
    }

    public GuildStorageServiceImpl(
            SqlGuildStorageStore store,
            GuildService guildService,
            ResidentService residentService,
            MainThreadExecutor mainThreadExecutor,
            Executor sqlExecutor) {
        this.store = Objects.requireNonNull(store, "store");
        this.guildService = Objects.requireNonNull(guildService, "guildService");
        this.residentService = Objects.requireNonNull(residentService, "residentService");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
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
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId) {
        return depositWithCompensation(
                actor, guildId, tabId, slotIndex, item, facilityId, UUID.randomUUID(), null);
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
        return executeMutation(
                operationId,
                compensationOnFailure,
                () -> persistDeposit(prepared.value().orElseThrow(), operationId));
    }

    @Override
    public StorageResult<OpaqueItemPayload> withdraw(
            UUID actor, String guildId, String tabId, int slotIndex, String facilityId) {
        return withdrawWithCompensation(
                actor, guildId, tabId, slotIndex, facilityId, UUID.randomUUID(), null);
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
        return executeMutation(
                operationId,
                compensationOnFailure,
                () -> persistWithdraw(prepared.value().orElseThrow(), operationId));
    }

    @Override
    public StorageResult<StoragePolicy> getPolicy(UUID actor, String guildId) {
        StorageResult<AccessContext> access = resolveAccess(actor, guildId);
        if (!access.isSuccess()) {
            return mapFailure(access);
        }
        try {
            store.getOrCreateBank(requireGuildId(guildId));
            return StorageResult.success(store.loadPolicy(guildId));
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
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(),
                GuildStorageRole.parse(store.loadPolicy(guildId).manageRole()));
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
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(), GuildStorageRole.parse(store.loadPolicy(guildId).depositRole()));
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
        if (facilityId == null || facilityId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "facilityId is required");
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

    private StorageResult<StorageSlot> persistDeposit(PreparedDeposit prepared, UUID operationId) {
        if (!store.saveSlot(prepared.guildId(), prepared.tabId(), prepared.slotIndex(), prepared.item(), 0L)) {
            return StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during deposit");
        }
        store.recordAudit(
                prepared.guildId(),
                prepared.actor(),
                "DEPOSIT",
                prepared.tabId(),
                prepared.slotIndex(),
                prepared.item().fingerprint(),
                prepared.facilityId() + ":" + operationId);
        StorageSlot slot = store.loadSlots(prepared.guildId(), prepared.tabId()).get(prepared.slotIndex());
        return StorageResult.success(slot);
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
        StorageResult<Void> permission = requireRole(
                access.value().orElseThrow(), GuildStorageRole.parse(store.loadPolicy(guildId).withdrawRole()));
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
        if (facilityId == null || facilityId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "facilityId is required");
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

    private StorageResult<OpaqueItemPayload> persistWithdraw(PreparedWithdraw prepared, UUID operationId) {
        StorageSlot occupied = prepared.occupied();
        if (!store.saveSlot(
                prepared.guildId(),
                prepared.tabId(),
                prepared.slotIndex(),
                null,
                occupied.version())) {
            return StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during withdraw");
        }
        store.recordAudit(
                prepared.guildId(),
                prepared.actor(),
                "WITHDRAW",
                prepared.tabId(),
                prepared.slotIndex(),
                occupied.item().fingerprint(),
                prepared.facilityId() + ":" + operationId);
        return StorageResult.success(occupied.item());
    }

    private <T> StorageResult<T> executeMutation(
            UUID operationId, Runnable compensationOnFailure, java.util.function.Supplier<StorageResult<T>> mutation) {
        StorageResult<?> existing = completedMutations.get(operationId);
        if (existing != null) {
            return castCached(existing);
        }
        try {
            StorageResult<T> result = CompletableFuture.supplyAsync(mutation, sqlExecutor).get();
            if (!result.isSuccess()) {
                runCompensation(compensationOnFailure);
            } else {
                completedMutations.putIfAbsent(operationId, result);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runCompensation(compensationOnFailure);
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Deposit interrupted");
        } catch (Exception e) {
            runCompensation(compensationOnFailure);
            return StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR,
                    e.getCause() instanceof RuntimeException runtime
                            ? runtime.getMessage()
                            : e.getMessage());
        }
    }

    private void runCompensation(Runnable compensationOnFailure) {
        if (compensationOnFailure != null) {
            mainThreadExecutor.run(compensationOnFailure);
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

    @SuppressWarnings("unchecked")
    private static <T> StorageResult<T> castCached(StorageResult<?> cached) {
        return (StorageResult<T>) cached;
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
