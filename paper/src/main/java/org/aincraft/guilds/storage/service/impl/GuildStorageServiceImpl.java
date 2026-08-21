package org.aincraft.guilds.storage.service.impl;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.AuditEvidenceLookupResult;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.persist.StorageOperationLookupResult;
import org.aincraft.guilds.storage.persist.StorageOperationRecord;
import org.aincraft.guilds.storage.persist.StorageOperationStatus;
import org.aincraft.guilds.storage.codec.ItemStackStorageCodec;
import org.aincraft.guilds.storage.persist.StorageDepositRestorationRecord;
import org.aincraft.guilds.storage.persist.StoragePayoutObligationRecord;
import org.aincraft.guilds.storage.persist.StoragePayoutObligationStatus;
import org.aincraft.guilds.storage.service.GuildStorageService;
import org.aincraft.guilds.storage.service.PayoutDeliveryHandoff;
import org.aincraft.guilds.storage.service.PlayerInventoryCoordinator;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.TimeUnit;
import org.aincraft.guilds.storage.service.MainThreadExecutor;
import org.aincraft.guilds.storage.service.StorageFacilityAccessValidator;
import org.aincraft.guilds.storage.service.StorageResult;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class GuildStorageServiceImpl implements GuildStorageService {
    private static final String JOURNAL_LOOKUP_FAILURE = "Failed to load storage operation journal";
    private static final String UNKNOWN_OUTCOME_MESSAGE =
            "Storage mutation outcome unknown; retry with same operationId";
    private static final String MUTATION_OUTCOME_UNKNOWN =
            "Storage mutation commit outcome unknown";
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
    private final PlayerInventoryCoordinator inventoryCoordinator;
    private final ItemStackStorageCodec payoutCodec = new ItemStackStorageCodec();

    public GuildStorageServiceImpl(
            SqlGuildStorageStore store,
            GuildService guildService,
            ResidentService residentService,
            StorageFacilityAccessValidator facilityAccess,
            MainThreadExecutor mainThreadExecutor,
            Executor sqlExecutor) {
        this(store, guildService, residentService, facilityAccess, mainThreadExecutor, sqlExecutor, null);
    }

    public GuildStorageServiceImpl(
            SqlGuildStorageStore store,
            GuildService guildService,
            ResidentService residentService,
            StorageFacilityAccessValidator facilityAccess,
            MainThreadExecutor mainThreadExecutor,
            Executor sqlExecutor,
            PlayerInventoryCoordinator inventoryCoordinator) {
        this.store = Objects.requireNonNull(store, "store");
        this.guildService = Objects.requireNonNull(guildService, "guildService");
        this.residentService = Objects.requireNonNull(residentService, "residentService");
        this.facilityAccess = Objects.requireNonNull(facilityAccess, "facilityAccess");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
        this.inventoryCoordinator = inventoryCoordinator;
        sqlExecutor.execute(this::reconcilePendingOperations);
        sqlExecutor.execute(this::reconcilePayoutObligations);
        sqlExecutor.execute(this::reconcileDepositRestorations);
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
        if (item == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "item is required");
        }
        StorageResult<Void> requestValidation = validateMutationRequest(operationId, facilityId);
        if (!requestValidation.isSuccess()) {
            return mapFailure(requestValidation);
        }
        StorageResult<StorageSlot> existing =
                resolveExistingOperation(operationId, "DEPOSIT", actor, guildId, tabId, slotIndex, facilityId, item);
        if (existing != null) {
            return existing;
        }
        StorageResult<Void> facilityValidation = validateFacilityAccessOnMainThread(actor, guildId, facilityId);
        if (!facilityValidation.isSuccess()) {
            runCompensation(compensationOnFailure);
            return mapFailure(facilityValidation);
        }
        StorageResult<PreparedDeposit> prepared =
                prepareDeposit(actor, guildId, tabId, slotIndex, item, facilityId, operationId);
        if (!prepared.isSuccess()) {
            runCompensation(compensationOnFailure);
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
                deposit.item(),
                compensationOnFailure,
                () -> persistDeposit(deposit, operationId));
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
        StorageResult<Void> requestValidation = validateMutationRequest(operationId, facilityId);
        if (!requestValidation.isSuccess()) {
            return mapFailure(requestValidation);
        }
        StorageResult<OpaqueItemPayload> existing =
                resolveExistingOperation(operationId, "WITHDRAW", actor, guildId, tabId, slotIndex, facilityId, null);
        if (existing != null) {
            return existing;
        }
        StorageResult<Void> facilityValidation = validateFacilityAccessOnMainThread(actor, guildId, facilityId);
        if (!facilityValidation.isSuccess()) {
            return mapFailure(facilityValidation);
        }
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
                withdraw.occupied().item(),
                compensationOnFailure,
                () -> persistWithdraw(withdraw, operationId));
    }

    /**
     * Restores a committed withdraw back into storage when payout to the player inventory fails
     * or the GUI session ends before payout completes.
     */
    public StorageResult<StorageSlot> compensateWithdrawPayout(
            UUID withdrawOperationId,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId) {
        return reinsertAuthorizedWithdrawPayout(
                withdrawOperationId, actor, guildId, tabId, slotIndex, item, facilityId);
    }

    public StorageResult<Void> confirmWithdrawPayoutDelivered(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        try {
            if (store.markPayoutObligationDelivered(withdrawOperationId, deliveryToken)) {
                return StorageResult.success(null);
            }
            return store.findPayoutObligation(withdrawOperationId)
                    .filter(obligation -> obligation.status() == StoragePayoutObligationStatus.DELIVERED)
                    .map(ignored -> StorageResult.<Void>success(null))
                    .orElseGet(() -> StorageResult.failure(
                            StorageResult.Status.CONFLICT, "Withdraw payout is not pending delivery"));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    public StorageResult<PayoutDeliveryHandoff> beginWithdrawPayoutDelivery(UUID withdrawOperationId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        try {
            Optional<UUID> deliveryToken = store.claimPayoutObligationForDelivery(withdrawOperationId);
            if (deliveryToken.isPresent()) {
                return StorageResult.success(new PayoutDeliveryHandoff(deliveryToken.get()));
            }
            return store.findPayoutObligation(withdrawOperationId)
                    .map(obligation -> switch (obligation.status()) {
                        case DELIVERED -> StorageResult.<PayoutDeliveryHandoff>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout already delivered");
                        case DELIVERING -> StorageResult.<PayoutDeliveryHandoff>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout delivery already in progress");
                        case UNKNOWN -> StorageResult.<PayoutDeliveryHandoff>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout delivery outcome unknown");
                        default -> StorageResult.<PayoutDeliveryHandoff>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout is not pending delivery");
                    })
                    .orElseGet(() -> StorageResult.failure(
                            StorageResult.Status.CONFLICT, "Withdraw payout obligation not found"));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    public StorageResult<Void> cancelWithdrawPayoutDelivery(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        try {
            if (store.releasePayoutObligationDeliveryClaim(withdrawOperationId, deliveryToken)) {
                return StorageResult.success(null);
            }
            return store.findPayoutObligation(withdrawOperationId)
                    .map(obligation -> switch (obligation.status()) {
                        case PENDING, DELIVERED, REINSERTED, UNKNOWN -> StorageResult.<Void>success(null);
                        default -> StorageResult.<Void>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout delivery handoff mismatch");
                    })
                    .orElseGet(() -> StorageResult.failure(
                            StorageResult.Status.CONFLICT, "Withdraw payout obligation not found"));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    public StorageResult<Void> markWithdrawPayoutDeliveryUnknown(UUID withdrawOperationId, UUID deliveryToken) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        Objects.requireNonNull(deliveryToken, "deliveryToken");
        try {
            if (store.markPayoutObligationDeliveryUnknown(withdrawOperationId, deliveryToken)) {
                return StorageResult.success(null);
            }
            return store.findPayoutObligation(withdrawOperationId)
                    .map(obligation -> switch (obligation.status()) {
                        case UNKNOWN -> StorageResult.<Void>success(null);
                        case DELIVERED -> StorageResult.<Void>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout already delivered");
                        default -> StorageResult.<Void>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout delivery handoff mismatch");
                    })
                    .orElseGet(() -> StorageResult.failure(
                            StorageResult.Status.CONFLICT, "Withdraw payout obligation not found"));
        } catch (RuntimeException e) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }


    public void acknowledgeDepositRestoration(UUID depositOperationId) {
        Objects.requireNonNull(depositOperationId, "depositOperationId");
        try {
            store.markDepositRestorationComplete(depositOperationId);
        } catch (RuntimeException ignored) {
            // Leave restoration obligation pending for startup reconciliation.
        }
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
        for (StorageOperationRecord pending : store.findPendingOperations()) {
            reconcilePendingOperation(pending);
        }
        for (StorageOperationRecord unknown : store.findUnknownOperations()) {
            reconcilePendingOperation(unknown);
        }
    }

    private void reconcilePendingOperation(StorageOperationRecord pending) {
        switch (pending.operationType()) {
            case "DEPOSIT" -> reconcilePendingDeposit(pending);
            case "WITHDRAW" -> reconcilePendingWithdraw(pending);
            default -> finalizeReconciliationFailure(
                    pending, "Unknown pending storage operation type: " + pending.operationType());
        }
    }

    private void reconcilePendingDeposit(StorageOperationRecord pending) {
        OpaqueItemPayload requestSnapshot = pending.requestSnapshot();
        if (requestSnapshot == null) {
            finalizeReconciliationFailure(
                    pending, "Pending deposit missing request snapshot; operator reconciliation required");
            return;
        }
        Map<Integer, StorageSlot> slots;
        try {
            slots = store.loadSlots(pending.guildId(), pending.tabId());
        } catch (RuntimeException e) {
            finalizeReconciliationUnknown(
                    pending, "Failed to load storage slots during reconciliation: " + e.getMessage());
            return;
        }
        StorageSlot slot = slots.get(pending.slotIndex());
        AuditEvidenceLookupResult auditLookup = store.lookupMatchingAudit(
                pending.operationId(),
                pending.guildId(),
                pending.actorUuid(),
                "DEPOSIT",
                pending.tabId(),
                pending.slotIndex(),
                pending.facilityId(),
                pending.createdAt(),
                requestSnapshot);
        if (auditLookup.status() == AuditEvidenceLookupResult.Status.READ_FAILURE) {
            finalizeReconciliationUnknown(pending, auditLookup.errorMessage());
            return;
        }
        boolean auditEvidence = auditLookup.status() == AuditEvidenceLookupResult.Status.MATCHING;
        if (auditEvidence) {
            StorageSlot committedSlot = replayableDepositSlot(pending, requestSnapshot, slot);
            store.finalizeOperation(
                    pending.operationId(),
                    StorageOperationStatus.COMMITTED,
                    StorageResult.Status.SUCCESS.name(),
                    null,
                    committedSlot,
                    requestSnapshot);
            return;
        }
        if (slot != null) {
            finalizeReconciliationUnknown(
                    pending,
                    "Pending deposit left slot occupied without matching audit evidence; operator reconciliation required");
            return;
        }
        finalizeReconciliationFailure(
                pending, "Pending deposit interrupted before durable slot and audit mutation");
    }

    private void reconcilePendingWithdraw(StorageOperationRecord pending) {
        OpaqueItemPayload requestSnapshot = pending.requestSnapshot();
        if (requestSnapshot == null) {
            finalizeReconciliationFailure(
                    pending, "Pending withdraw missing request snapshot; operator reconciliation required");
            return;
        }
        Map<Integer, StorageSlot> slots;
        try {
            slots = store.loadSlots(pending.guildId(), pending.tabId());
        } catch (RuntimeException e) {
            finalizeReconciliationUnknown(
                    pending, "Failed to load storage slots during reconciliation: " + e.getMessage());
            return;
        }
        StorageSlot slot = slots.get(pending.slotIndex());
        AuditEvidenceLookupResult auditLookup = store.lookupMatchingAudit(
                pending.operationId(),
                pending.guildId(),
                pending.actorUuid(),
                "WITHDRAW",
                pending.tabId(),
                pending.slotIndex(),
                pending.facilityId(),
                pending.createdAt(),
                requestSnapshot);
        if (auditLookup.status() == AuditEvidenceLookupResult.Status.READ_FAILURE) {
            finalizeReconciliationUnknown(pending, auditLookup.errorMessage());
            return;
        }
        boolean auditEvidence = auditLookup.status() == AuditEvidenceLookupResult.Status.MATCHING;
        if (auditEvidence) {
            store.finalizeOperation(
                    pending.operationId(),
                    StorageOperationStatus.COMMITTED,
                    StorageResult.Status.SUCCESS.name(),
                    null,
                    null,
                    requestSnapshot);
            return;
        }
        if (slot == null) {
            finalizeReconciliationFailure(
                    pending, "Pending withdraw slot empty without matching audit evidence");
            return;
        }
        finalizeReconciliationFailure(
                pending, "Pending withdraw interrupted before durable slot and audit mutation");
    }

    private void finalizeReconciliationFailure(StorageOperationRecord pending, String errorMessage) {
        store.finalizeOperation(
                pending.operationId(),
                StorageOperationStatus.COMPENSATED,
                StorageResult.Status.STORAGE_ERROR.name(),
                errorMessage,
                null,
                null);
    }

    private void finalizeReconciliationUnknown(StorageOperationRecord pending, String errorMessage) {
        store.finalizeOperation(
                pending.operationId(),
                StorageOperationStatus.UNKNOWN,
                StorageResult.Status.STORAGE_ERROR.name(),
                errorMessage,
                null,
                null);
    }

    private static StorageSlot replayableDepositSlot(
            StorageOperationRecord pending, OpaqueItemPayload requestSnapshot, StorageSlot liveSlot) {
        if (liveSlot != null) {
            return new StorageSlot(
                    pending.guildId(),
                    pending.tabId(),
                    pending.slotIndex(),
                    requestSnapshot,
                    liveSlot.version(),
                    liveSlot.updatedAt());
        }
        return new StorageSlot(
                pending.guildId(),
                pending.tabId(),
                pending.slotIndex(),
                requestSnapshot,
                1L,
                pending.createdAt());
    }




    private void reconcilePayoutObligations() {
        if (inventoryCoordinator == null) {
            return;
        }
        for (StoragePayoutObligationRecord obligation : store.findOutstandingPayoutObligations()) {
            reconcilePayoutObligation(obligation);
        }
    }

    private void reconcilePayoutObligation(StoragePayoutObligationRecord obligation) {
        if (tryDeliverPayoutOnline(obligation)) {
            return;
        }
        reinsertAuthorizedWithdrawPayout(
                obligation.withdrawOperationId(),
                obligation.actorUuid(),
                obligation.guildId(),
                obligation.tabId(),
                obligation.slotIndex(),
                obligation.item(),
                obligation.facilityId());
    }

    private void reconcileDepositRestorations() {
        if (inventoryCoordinator == null) {
            return;
        }
        for (StorageDepositRestorationRecord restoring : store.findRestoringDepositRestorations()) {
            store.releaseDepositRestorationClaim(restoring.depositOperationId());
        }
        for (StorageDepositRestorationRecord obligation : store.findPendingDepositRestorations()) {
            if (!store.claimDepositRestorationForDelivery(obligation.depositOperationId())) {
                continue;
            }
            ItemStack item = payoutCodec.decode(obligation.item());
            CompletableFuture<Boolean> restored = new CompletableFuture<>();
            mainThreadExecutor.run(() -> inventoryCoordinator.giveItem(
                    obligation.actorUuid(), item, restored::complete));
            try {
                if (Boolean.TRUE.equals(restored.get(5, TimeUnit.SECONDS))) {
                    acknowledgeDepositRestoration(obligation.depositOperationId());
                } else {
                    store.releaseDepositRestorationClaim(obligation.depositOperationId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                store.releaseDepositRestorationClaim(obligation.depositOperationId());
            } catch (Exception ignored) {
                store.releaseDepositRestorationClaim(obligation.depositOperationId());
            }
        }
    }

    private boolean tryDeliverPayoutOnline(StoragePayoutObligationRecord obligation) {
        if (inventoryCoordinator == null) {
            return false;
        }
        if (obligation.status() == StoragePayoutObligationStatus.DELIVERED) {
            return true;
        }
        if (obligation.status() == StoragePayoutObligationStatus.UNKNOWN) {
            return false;
        }
        if (obligation.status() == StoragePayoutObligationStatus.DELIVERING) {
            store.cancelPayoutDeliveryForReinsertion(obligation.withdrawOperationId());
            Optional<StoragePayoutObligationRecord> refreshed =
                    store.findPayoutObligation(obligation.withdrawOperationId());
            if (refreshed.isEmpty() || refreshed.get().status() != StoragePayoutObligationStatus.PENDING) {
                return false;
            }
            obligation = refreshed.get();
        }
        StorageResult<PayoutDeliveryHandoff> claimed = beginWithdrawPayoutDelivery(obligation.withdrawOperationId());
        if (!claimed.isSuccess()) {
            return false;
        }
        UUID deliveryToken = claimed.value().orElseThrow().deliveryToken();
        UUID withdrawOperationId = obligation.withdrawOperationId();
        UUID actorUuid = obligation.actorUuid();
        CompletableFuture<Boolean> delivered = new CompletableFuture<>();
        ItemStack item = payoutCodec.decode(obligation.item());
        mainThreadExecutor.run(() -> inventoryCoordinator.giveItem(
                actorUuid, item, delivered::complete));
        try {
            if (Boolean.TRUE.equals(delivered.get(5, TimeUnit.SECONDS))) {
                if (!isPayoutDeliveryClaimActive(withdrawOperationId, deliveryToken)) {
                    return false;
                }
                StorageResult<Void> confirmed = confirmWithdrawPayoutDelivered(withdrawOperationId, deliveryToken);
                if (confirmed.isSuccess()) {
                    return true;
                }
                store.markPayoutObligationDeliveryUnknown(withdrawOperationId, deliveryToken);
                return false;
            }
            if (isPayoutDeliveryClaimActive(withdrawOperationId, deliveryToken)) {
                cancelWithdrawPayoutDelivery(withdrawOperationId, deliveryToken);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (isPayoutDeliveryClaimActive(withdrawOperationId, deliveryToken)) {
                cancelWithdrawPayoutDelivery(withdrawOperationId, deliveryToken);
            }
        } catch (Exception ignored) {
            if (isPayoutDeliveryClaimActive(withdrawOperationId, deliveryToken)) {
                cancelWithdrawPayoutDelivery(withdrawOperationId, deliveryToken);
            }
        }
        return false;
    }

    private boolean isPayoutDeliveryClaimActive(UUID withdrawOperationId, UUID deliveryToken) {
        return store.findPayoutObligation(withdrawOperationId)
                .filter(obligation -> obligation.status() == StoragePayoutObligationStatus.DELIVERING
                        && deliveryToken.equals(obligation.deliveryToken()))
                .isPresent();
    }

    private StorageResult<StorageSlot> reinsertAuthorizedWithdrawPayout(
            UUID withdrawOperationId,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId) {
        Objects.requireNonNull(withdrawOperationId, "withdrawOperationId");
        if (item == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "item is required");
        }
        StorageOperationLookupResult withdrawLookup = store.lookupOperation(withdrawOperationId);
        if (withdrawLookup.status() == StorageOperationLookupResult.Status.READ_FAILURE) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, JOURNAL_LOOKUP_FAILURE);
        }
        if (withdrawLookup.status() == StorageOperationLookupResult.Status.NOT_FOUND) {
            return StorageResult.failure(
                    StorageResult.Status.CONFLICT, "Withdraw operation is not eligible for payout compensation");
        }
        StorageOperationRecord withdrawOperation = withdrawLookup.record().orElseThrow();
        if (withdrawOperation.status() != StorageOperationStatus.COMMITTED
                || !"WITHDRAW".equals(withdrawOperation.operationType())) {
            return StorageResult.failure(
                    StorageResult.Status.CONFLICT, "Withdraw operation is not eligible for payout compensation");
        }
        if (!withdrawOperation.actorUuid().equals(actor)
                || !withdrawOperation.guildId().equals(guildId)
                || !withdrawOperation.tabId().equals(tabId)
                || withdrawOperation.slotIndex() != slotIndex
                || !withdrawOperation.facilityId().equals(facilityId.trim())) {
            return StorageResult.failure(
                    StorageResult.Status.CONFLICT, "Withdraw operation identity mismatch");
        }
        OpaqueItemPayload authorizedItem = withdrawOperation.resultItem();
        if (authorizedItem == null) {
            authorizedItem = withdrawOperation.requestSnapshot();
        }
        if (authorizedItem == null || !authorizedItem.equals(item)) {
            return StorageResult.failure(
                    StorageResult.Status.CONFLICT, "Withdraw payout item mismatch");
        }
        if (!store.cancelPayoutDeliveryForReinsertion(withdrawOperationId)) {
            return store.findPayoutObligation(withdrawOperationId)
                    .map(obligation -> switch (obligation.status()) {
                        case PENDING, UNKNOWN -> StorageResult.<StorageSlot>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout delivery is still active");
                        default -> StorageResult.<StorageSlot>failure(
                                StorageResult.Status.CONFLICT, "Withdraw payout is not eligible for reinsertion");
                    })
                    .orElseGet(() -> StorageResult.failure(
                            StorageResult.Status.CONFLICT, "Withdraw payout obligation not found"));
        }
        UUID reinsertOperationId = resolveReinsertOperationId(withdrawOperationId);
        store.assignPayoutReinsertAttempt(withdrawOperationId, reinsertOperationId);
        StorageResult<StorageSlot> existing = resolveExistingOperation(
                reinsertOperationId,
                "PAYOUT_REINSERT",
                actor,
                guildId,
                tabId,
                slotIndex,
                facilityId,
                item);
        if (existing != null) {
            return existing;
        }
        if (!store.insertPendingOperation(
                reinsertOperationId,
                guildId,
                "PAYOUT_REINSERT",
                actor,
                tabId,
                slotIndex,
                facilityId,
                item)) {
            StorageOperationLookupResult replay = store.lookupOperation(reinsertOperationId);
            if (replay.status() == StorageOperationLookupResult.Status.FOUND) {
                return replayExistingReinsert(replay.record().orElseThrow(), item);
            }
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, JOURNAL_LOOKUP_FAILURE);
        }
        try {
            SqlGuildStorageStore.ReinsertWithdrawPayoutOutcome outcome = store.reinsertWithdrawPayoutWithAudit(
                    withdrawOperationId,
                    reinsertOperationId,
                    guildId,
                    tabId,
                    slotIndex,
                    item,
                    actor,
                    facilityId);
            StorageResult<StorageSlot> result = switch (outcome.status()) {
                case SUCCESS -> StorageResult.success(outcome.slot());
                case CONFLICT -> StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during payout reinsert");
                case FAILED -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Failed to reinsert withdraw payout");
                case UNKNOWN -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, MUTATION_OUTCOME_UNKNOWN);
            };
            if (result.isSuccess()) {
                store.finalizeOperation(
                        reinsertOperationId,
                        StorageOperationStatus.COMMITTED,
                        result.status().name(),
                        null,
                        result.value().orElseThrow(),
                        item);
            } else if (isUnknownMutationResult(result)) {
                preserveUnknownOutcome(reinsertOperationId, result.errorMessage());
            } else {
                store.finalizeOperation(
                        reinsertOperationId,
                        StorageOperationStatus.COMPENSATED,
                        result.status().name(),
                        result.errorMessage(),
                        null,
                        null);
            }
            return result;
        } catch (RuntimeException e) {
            preserveUnknownOutcome(reinsertOperationId, e.getMessage());
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, e.getMessage());
        }
    }

    private StorageResult<StorageSlot> replayExistingReinsert(
            StorageOperationRecord operation, OpaqueItemPayload requestSnapshot) {
        if (operation.status() == StorageOperationStatus.COMMITTED
                && StorageResult.Status.SUCCESS.name().equals(operation.resultStatus())) {
            if (operation.resultSlot() != null) {
                return StorageResult.success(operation.resultSlot());
            }
            if (requestSnapshot != null) {
                return StorageResult.success(
                        new StorageSlot(
                                operation.guildId(),
                                operation.tabId(),
                                operation.slotIndex(),
                                requestSnapshot,
                                1L,
                                operation.updatedAt()));
            }
        }
        return replayOperation(operation);
    }

    private StorageResult<Void> validateMutationRequest(UUID operationId, String facilityId) {
        if (operationId == null) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "operationId is required");
        }
        if (facilityId == null || facilityId.isBlank()) {
            return StorageResult.failure(StorageResult.Status.INVALID_ARGUMENT, "facilityId is required");
        }
        return StorageResult.success(null);
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

    private StorageResult<StorageSlot> persistDeposit(PreparedDeposit prepared, UUID operationId) {
        SqlGuildStorageStore.DepositAuditOutcome outcome = store.depositWithAudit(
                prepared.guildId(),
                prepared.tabId(),
                prepared.slotIndex(),
                prepared.item(),
                prepared.actor(),
                prepared.facilityId(),
                operationId);
        return switch (outcome.status()) {
            case SUCCESS -> StorageResult.success(outcome.slot());
            case CONFLICT -> StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during deposit");
            case FAILED -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Failed to persist deposit");
            case UNKNOWN -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, MUTATION_OUTCOME_UNKNOWN);
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

    private StorageResult<OpaqueItemPayload> persistWithdraw(PreparedWithdraw prepared, UUID operationId) {
        StorageSlot occupied = prepared.occupied();
        SqlGuildStorageStore.WithdrawAuditOutcome outcome = store.withdrawWithAudit(
                prepared.guildId(),
                prepared.tabId(),
                prepared.slotIndex(),
                occupied.item(),
                occupied.version(),
                prepared.actor(),
                prepared.facilityId(),
                operationId);
        return switch (outcome.status()) {
            case SUCCESS -> StorageResult.success(outcome.item());
            case CONFLICT -> StorageResult.failure(StorageResult.Status.CONFLICT, "Slot changed during withdraw");
            case FAILED -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, "Failed to persist withdraw");
            case UNKNOWN -> StorageResult.failure(StorageResult.Status.STORAGE_ERROR, MUTATION_OUTCOME_UNKNOWN);
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
            OpaqueItemPayload requestSnapshot,
            Runnable compensationOnFailure,
            java.util.function.Supplier<StorageResult<T>> mutation) {
        if (!store.insertPendingOperation(
                operationId,
                guildId,
                operationType,
                actor,
                tabId,
                slotIndex,
                facilityId,
                requestSnapshot)) {
            StorageOperationLookupResult existing = store.lookupOperation(operationId);
            if (existing.status() == StorageOperationLookupResult.Status.READ_FAILURE) {
                return StorageResult.failure(
                        StorageResult.Status.STORAGE_ERROR,
                        JOURNAL_LOOKUP_FAILURE + " after insert conflict");
            }
            if (existing.status() == StorageOperationLookupResult.Status.NOT_FOUND) {
                return StorageResult.failure(
                        StorageResult.Status.STORAGE_ERROR,
                        JOURNAL_LOOKUP_FAILURE + " after insert conflict");
            }
            if (!matchesOperationRequest(
                    existing.record().orElseThrow(),
                    operationType,
                    actor,
                    guildId,
                    tabId,
                    slotIndex,
                    facilityId,
                    requestSnapshot)) {
                return StorageResult.failure(
                        StorageResult.Status.CONFLICT, "Storage operation identity mismatch");
            }
            return replayOperation(existing.record().orElseThrow());
        }
        try {
            StorageResult<T> result = CompletableFuture.supplyAsync(mutation, sqlExecutor).get();
            if (isUnknownMutationResult(result)) {
                preserveUnknownOutcome(operationId, UNKNOWN_OUTCOME_MESSAGE);
                return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, UNKNOWN_OUTCOME_MESSAGE);
            }
            if (!finalizeOperationJournal(operationId, operationType, result, compensationOnFailure) && result.isSuccess()) {
                return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, UNKNOWN_OUTCOME_MESSAGE);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            preserveUnknownOutcome(operationId, "Storage mutation interrupted; outcome unknown");
            return StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR, "Storage mutation interrupted; outcome unknown");
        } catch (Exception e) {
            StorageResult<T> failure = StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR,
                    e.getCause() instanceof RuntimeException runtime
                            ? runtime.getMessage()
                            : e.getMessage());
            if (!finalizeOperationJournal(operationId, operationType, failure, compensationOnFailure)) {
                if ("DEPOSIT".equals(operationType)) {
                    recordDepositRestorationObligation(operationId);
                    runCompensation(compensationOnFailure);
                }
                preserveUnknownOutcome(operationId, UNKNOWN_OUTCOME_MESSAGE);
            }
            return failure;
        }
    }


    private <T> boolean finalizeOperationJournal(
            UUID operationId,
            String operationType,
            StorageResult<T> result,
            Runnable compensationOnFailure) {
        try {
            if (result.isSuccess()) {
                store.finalizeOperation(
                        operationId,
                        StorageOperationStatus.COMMITTED,
                        result.status().name(),
                        null,
                        resultSlot(result),
                        resultItem(result));
            } else if (isUnknownMutationResult(result)) {
                preserveUnknownOutcome(operationId, result.errorMessage());
            } else {
                store.finalizeOperation(
                        operationId,
                        StorageOperationStatus.COMPENSATED,
                        result.status().name(),
                        result.errorMessage(),
                        null,
                        null);
                if ("DEPOSIT".equals(operationType)) {
                    recordDepositRestorationObligation(operationId);
                    runCompensation(compensationOnFailure);
                }
            }
            return true;
        } catch (RuntimeException e) {
            if (!result.isSuccess() && "DEPOSIT".equals(operationType)) {
                runCompensation(compensationOnFailure);
            }
            preserveUnknownOutcome(
                    operationId,
                    result.isSuccess()
                            ? "Storage mutation succeeded but journal finalize failed"
                            : "Storage mutation failed but journal finalize failed");
            return false;
        }
    }

    private void preserveUnknownOutcome(UUID operationId, String errorMessage) {
        try {
            store.finalizeOperation(
                    operationId,
                    StorageOperationStatus.UNKNOWN,
                    StorageResult.Status.STORAGE_ERROR.name(),
                    errorMessage,
                    null,
                    null);
        } catch (RuntimeException ignored) {
            // Leave the journal row pending for operator reconciliation.
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
    private <T> StorageResult<T> resolveExistingOperation(
            UUID operationId,
            String operationType,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            OpaqueItemPayload requestSnapshot) {
        StorageOperationLookupResult existing = store.lookupOperation(operationId);
        if (existing.status() == StorageOperationLookupResult.Status.READ_FAILURE) {
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, JOURNAL_LOOKUP_FAILURE);
        }
        if (existing.status() == StorageOperationLookupResult.Status.NOT_FOUND) {
            return null;
        }
        if (!matchesOperationRequest(
                existing.record().orElseThrow(),
                operationType,
                actor,
                guildId,
                tabId,
                slotIndex,
                facilityId,
                requestSnapshot)) {
            return StorageResult.failure(StorageResult.Status.CONFLICT, "Storage operation identity mismatch");
        }
        return replayOperation(existing.record().orElseThrow());
    }

    private static boolean matchesOperationRequest(
            StorageOperationRecord operation,
            String operationType,
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            String facilityId,
            OpaqueItemPayload requestSnapshot) {
        if (!operation.operationType().equals(operationType)) {
            return false;
        }
        if (!operation.actorUuid().equals(actor)) {
            return false;
        }
        if (!operation.guildId().equals(guildId)) {
            return false;
        }
        if (!operation.tabId().equals(tabId)) {
            return false;
        }
        if (operation.slotIndex() != slotIndex) {
            return false;
        }
        if (!operation.facilityId().equals(facilityId.trim())) {
            return false;
        }
        if (requestSnapshot != null) {
            OpaqueItemPayload storedSnapshot = storedOperationSnapshot(operation);
            return storedSnapshot != null && requestSnapshot.equals(storedSnapshot);
        }
        return true;
    }

    private static OpaqueItemPayload storedOperationSnapshot(StorageOperationRecord operation) {
        if (operation.requestSnapshot() != null) {
            return operation.requestSnapshot();
        }
        if (operation.status() == StorageOperationStatus.COMMITTED && operation.resultSlot() != null) {
            return operation.resultSlot().item();
        }
        return operation.resultItem();
    }

    private static boolean isUnknownMutationResult(StorageResult<?> result) {
        return !result.isSuccess() && MUTATION_OUTCOME_UNKNOWN.equals(result.errorMessage());
    }

    @SuppressWarnings("unchecked")
    private <T> StorageResult<T> replayOperation(StorageOperationRecord operation) {
        if (operation.status() == StorageOperationStatus.PENDING) {
            return StorageResult.failure(
                    StorageResult.Status.CONFLICT, "Storage operation already in progress");
        }
        if (operation.status() == StorageOperationStatus.UNKNOWN) {
            return StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR,
                    operation.resultError() != null ? operation.resultError() : UNKNOWN_OUTCOME_MESSAGE);
        }
        StorageResult.Status status = StorageResult.Status.valueOf(operation.resultStatus());
        if (operation.status() == StorageOperationStatus.COMMITTED && status == StorageResult.Status.SUCCESS) {
            if ("DEPOSIT".equals(operation.operationType())) {
                if (operation.resultSlot() != null) {
                    return (StorageResult<T>) StorageResult.success(operation.resultSlot());
                }
                OpaqueItemPayload requestSnapshot = operation.requestSnapshot();
                if (requestSnapshot != null) {
                    return (StorageResult<T>) StorageResult.success(
                            replayableDepositSlot(operation, requestSnapshot, null));
                }
            }
            if ("WITHDRAW".equals(operation.operationType()) && operation.resultItem() != null) {
                return (StorageResult<T>) StorageResult.success(operation.resultItem());
            }
            if ("PAYOUT_REINSERT".equals(operation.operationType())) {
                if (operation.resultSlot() != null) {
                    return (StorageResult<T>) StorageResult.success(operation.resultSlot());
                }
                OpaqueItemPayload requestSnapshot = operation.requestSnapshot();
                if (requestSnapshot != null) {
                    return (StorageResult<T>) StorageResult.success(
                            new StorageSlot(
                                    operation.guildId(),
                                    operation.tabId(),
                                    operation.slotIndex(),
                                    requestSnapshot,
                                    1L,
                                    operation.updatedAt()));
                }
            }
        }
        return StorageResult.failure(status, operation.resultError());
    }

    private StorageResult<Void> validateFacilityAccessOnMainThread(UUID actor, String guildId, String facilityId) {
        CompletableFuture<StorageResult<Void>> validation = new CompletableFuture<>();
        mainThreadExecutor.run(() ->
                validation.complete(facilityAccess.validateMutationAccess(actor, guildId, facilityId)));
        try {
            return validation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StorageResult.failure(
                    StorageResult.Status.STORAGE_ERROR, "Storage access validation interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            return StorageResult.failure(StorageResult.Status.STORAGE_ERROR, cause.getMessage());
        }
    }

    private UUID resolveReinsertOperationId(UUID withdrawOperationId) {
        Optional<StoragePayoutObligationRecord> obligation = store.findPayoutObligation(withdrawOperationId);
        if (obligation.isEmpty() || obligation.get().reinsertOperationId() == null) {
            return UUID.randomUUID();
        }
        UUID existingId = obligation.get().reinsertOperationId();
        StorageOperationLookupResult existing = store.lookupOperation(existingId);
        if (existing.status() == StorageOperationLookupResult.Status.FOUND) {
            StorageOperationRecord operation = existing.record().orElseThrow();
            if (operation.status() == StorageOperationStatus.PENDING
                    || operation.status() == StorageOperationStatus.UNKNOWN) {
                return existingId;
            }
        }
        return UUID.randomUUID();
    }

    private void recordDepositRestorationObligation(UUID operationId) {
        StorageOperationLookupResult lookup = store.lookupOperation(operationId);
        if (lookup.status() != StorageOperationLookupResult.Status.FOUND) {
            return;
        }
        StorageOperationRecord operation = lookup.record().orElseThrow();
        OpaqueItemPayload item = operation.requestSnapshot();
        if (item == null) {
            return;
        }
        store.insertDepositRestorationObligation(
                operationId,
                operation.guildId(),
                operation.actorUuid(),
                operation.tabId(),
                operation.slotIndex(),
                operation.facilityId(),
                item);
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
