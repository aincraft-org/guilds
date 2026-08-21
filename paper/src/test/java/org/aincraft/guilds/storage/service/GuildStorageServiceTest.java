package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.persist.StorageOperationRecord;
import org.aincraft.guilds.storage.persist.StorageOperationStatus;
import org.aincraft.guilds.storage.service.impl.GuildStorageServiceImpl;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuildStorageServiceTest {
    @Mock
    private SqlGuildStorageStore store;

    @Mock
    private GuildService guildService;

    @Mock
    private ResidentService residentService;

    @Mock
    private StorageFacilityAccessValidator facilityAccess;

    private GuildStorageServiceImpl storageService;
    private String guildId;
    private UUID mayorId;
    private UUID memberId;
    private Guild guild;

    @BeforeEach
    void setUp() {
        storageService = GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);
        guildId = "guild-1";
        mayorId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        guild = new Guild("Storage Guild", mayorId);
        guild.setId(guildId);
        guild.addResident(memberId);

        when(guildService.getGuildById(guildId)).thenReturn(Optional.of(guild));
        when(store.getOrCreateBank(guildId))
                .thenReturn(new GuildStorageBank(guildId, 1, Instant.EPOCH, Instant.EPOCH));
        when(store.loadTabs(guildId))
                .thenReturn(List.of(new StorageTab(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        "General",
                        0,
                        SqlGuildStorageStore.DEFAULT_TAB_CAPACITY,
                        true)));
        when(store.loadPolicy(guildId))
                .thenReturn(new StoragePolicy(
                        guildId, "MEMBER", "ASSISTANT", "MAYOR", Instant.parse("2026-08-21T12:00:00Z")));
        when(facilityAccess.validateMutationAccess(any(), eq(guildId), any()))
                .thenReturn(StorageResult.success(null));
        when(store.findOperation(any())).thenReturn(Optional.empty());
        when(store.insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        when(store.findPendingOperations()).thenReturn(List.of());
    }

    @Test
    void depositByMemberSucceedsAndRecordsAudit() {
        Resident member = member("Member", memberId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-deposit", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));

        when(residentService.getResident(memberId)).thenReturn(Optional.of(member));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.depositWithAudit(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        4,
                        payload,
                        memberId,
                        "facility-1"))
                .thenReturn(new SqlGuildStorageStore.DepositAuditOutcome(
                        SqlGuildStorageStore.SlotMutationResult.SUCCESS, saved));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                payload,
                "facility-1");

        assertTrue(result.isSuccess(), () -> result.status() + ": " + result.errorMessage());
        assertEquals(payload, result.value().orElseThrow().item());
        verify(store).depositWithAudit(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, memberId, "facility-1");
        verify(store, never()).saveSlot(any(), any(), anyInt(), any(), anyLong());
    }

    @Test
    void depositByOutsiderIsUnauthorized() {
        UUID outsider = UUID.randomUUID();
        when(residentService.getResident(outsider)).thenReturn(Optional.of(member("Outsider", outsider)));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                outsider,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "facility-1");

        assertEquals(StorageResult.Status.UNAUTHORIZED, result.status());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void depositDeniedWhenFacilityAccessFails() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(facilityAccess.validateMutationAccess(memberId, guildId, "foreign-facility"))
                .thenReturn(StorageResult.failure(
                        StorageResult.Status.PERMISSION_DENIED, "Storage facility is not governed by guild"));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                2,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "foreign-facility");

        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void withdrawByMemberIsPermissionDenied() {
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp", "payload");
        StorageSlot occupied = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(2, occupied));

        StorageResult<OpaqueItemPayload> result = storageService.withdraw(
                UUID.randomUUID(), memberId, guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, "facility-1");

        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
        verify(store, never()).withdrawWithAudit(any(), any(), anyInt(), any(), anyLong(), any(), any());
    }

    @Test
    void depositIntoOccupiedSlotIsRejected() {
        OpaqueItemPayload existing = new OpaqueItemPayload("paper-bytes-v1", "fp-existing", "existing");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID))
                .thenReturn(Map.of(6, new StorageSlot(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        6,
                        existing,
                        1L,
                        Instant.parse("2026-08-21T12:00:00Z"))));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                6,
                new OpaqueItemPayload("paper-bytes-v1", "fp-new", "new"),
                "facility-1");

        assertEquals(StorageResult.Status.SLOT_OCCUPIED, result.status());
    }

    @Test
    void withdrawFromEmptySlotIsRejected() {
        when(residentService.getResident(mayorId)).thenReturn(Optional.of(member("Mayor", mayorId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());

        StorageResult<OpaqueItemPayload> result = storageService.withdraw(
                UUID.randomUUID(), mayorId, guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 8, "facility-1");

        assertEquals(StorageResult.Status.SLOT_EMPTY, result.status());
    }


    @Test
    void pendingOperationRetryReturnsConflictWithoutSecondMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-pending", "payload");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.findOperation(operationId))
                .thenReturn(Optional.of(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        StorageOperationStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        createdAt,
                        createdAt)));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertEquals("Storage operation already in progress", result.errorMessage());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void concurrentRetryWhileOperationPendingPerformsOneMutationAndReturnsConflict() throws Exception {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-concurrent", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 3, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                3,
                "facility-1",
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        AtomicInteger findCalls = new AtomicInteger();
        when(store.findOperation(operationId)).thenAnswer(invocation -> {
            if (findCalls.incrementAndGet() == 1) {
                return Optional.empty();
            }
            return Optional.of(pending);
        });
        when(store.insertPendingOperation(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        3,
                        "facility-1",
                        payload))
                .thenReturn(true);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        CountDownLatch allowMutationComplete = new CountDownLatch(1);
        AtomicInteger mutationCalls = new AtomicInteger();
        when(store.depositWithAudit(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        3,
                        payload,
                        memberId,
                        "facility-1"))
                .thenAnswer(invocation -> {
                    mutationCalls.incrementAndGet();
                    mutationStarted.countDown();
                    assertTrue(allowMutationComplete.await(5, TimeUnit.SECONDS));
                    return new SqlGuildStorageStore.DepositAuditOutcome(
                            SqlGuildStorageStore.SlotMutationResult.SUCCESS, saved);
                });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<StorageResult<StorageSlot>> leader =
                    pool.submit(() -> storageService.deposit(
                            operationId,
                            memberId,
                            guildId,
                            SqlGuildStorageStore.DEFAULT_TAB_ID,
                            3,
                            payload,
                            "facility-1"));
            assertTrue(mutationStarted.await(5, TimeUnit.SECONDS));

            Future<StorageResult<StorageSlot>> retry =
                    pool.submit(() -> storageService.deposit(
                            operationId,
                            memberId,
                            guildId,
                            SqlGuildStorageStore.DEFAULT_TAB_ID,
                            3,
                            payload,
                            "facility-1"));

            StorageResult<StorageSlot> retryResult = retry.get(5, TimeUnit.SECONDS);
            assertEquals(StorageResult.Status.CONFLICT, retryResult.status());
            assertEquals("Storage operation already in progress", retryResult.errorMessage());

            allowMutationComplete.countDown();
            StorageResult<StorageSlot> leaderResult = leader.get(5, TimeUnit.SECONDS);
            assertTrue(leaderResult.isSuccess());
            assertEquals(1, mutationCalls.get());
            verify(store, org.mockito.Mockito.times(1))
                    .depositWithAudit(
                            guildId,
                            SqlGuildStorageStore.DEFAULT_TAB_ID,
                            3,
                            payload,
                            memberId,
                            "facility-1");
        } finally {
            allowMutationComplete.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateOperationReturnsStoredResultWithoutSecondMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-dup", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.findOperation(operationId))
                .thenReturn(Optional.of(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        StorageOperationStatus.COMMITTED,
                        StorageResult.Status.SUCCESS.name(),
                        null,
                        payload,
                        saved,
                        Instant.parse("2026-08-21T12:00:00Z"),
                        Instant.parse("2026-08-21T12:00:00Z"))));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                payload,
                "facility-1");

        assertTrue(result.isSuccess());
        assertEquals(saved, result.value().orElseThrow());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void policyLoadFailureReturnsStorageError() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadPolicy(guildId)).thenThrow(new IllegalStateException("policy table missing"));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                3,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void depositRunsCompensationOnMainThreadWhenSqlPersistFails() {
        Resident member = member("Member", memberId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-comp", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.depositWithAudit(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        5,
                        payload,
                        memberId,
                        "facility-1"))
                .thenReturn(new SqlGuildStorageStore.DepositAuditOutcome(
                        SqlGuildStorageStore.SlotMutationResult.CONFLICT, null));
        AtomicBoolean compensated = new AtomicBoolean();
        AtomicReference<Thread> compensationThread = new AtomicReference<>();
        GuildStorageServiceImpl service = new GuildStorageServiceImpl(
                store,
                guildService,
                residentService,
                facilityAccess,
                task -> {
                    compensationThread.set(Thread.currentThread());
                    task.run();
                    compensated.set(true);
                },
                Runnable::run);
        UUID operationId = UUID.randomUUID();

        StorageResult<StorageSlot> result = service.depositWithCompensation(
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                5,
                payload,
                "facility-1",
                operationId,
                () -> {});

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertTrue(compensated.get());
        assertEquals(Thread.currentThread(), compensationThread.get());
    }

    @Test
    void withdrawRunsCompensationOnMainThreadWhenSqlPersistFails() {
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-withdraw", "payload");
        StorageSlot occupied = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 9, payload, 3L, Instant.parse("2026-08-21T12:00:00Z"));
        when(residentService.getResident(mayorId)).thenReturn(Optional.of(member("Mayor", mayorId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(9, occupied));
        when(store.withdrawWithAudit(
                        guildId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        9,
                        payload,
                        3L,
                        mayorId,
                        "facility-1"))
                .thenReturn(new SqlGuildStorageStore.WithdrawAuditOutcome(
                        SqlGuildStorageStore.SlotMutationResult.CONFLICT, null));
        AtomicBoolean compensated = new AtomicBoolean();
        GuildStorageServiceImpl service = new GuildStorageServiceImpl(
                store,
                guildService,
                residentService,
                facilityAccess,
                task -> {
                    task.run();
                    compensated.set(true);
                },
                Runnable::run);
        UUID operationId = UUID.randomUUID();

        StorageResult<OpaqueItemPayload> result = service.withdrawWithCompensation(
                mayorId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                9,
                "facility-1",
                operationId,
                () -> {});

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertTrue(compensated.get());
    }

    @Test
    void invalidPhysicalAccessFailsBeforeStorageSqlWhenDatabaseUnavailable() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(facilityAccess.validateMutationAccess(memberId, guildId, "foreign-facility"))
                .thenReturn(StorageResult.failure(
                        StorageResult.Status.PERMISSION_DENIED, "Actor is not at storage facility"));
        when(store.loadPolicy(guildId)).thenThrow(new IllegalStateException("policy table missing"));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                2,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "foreign-facility");

        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
        verify(store, never()).loadPolicy(guildId);
        verify(store, never()).loadTabs(any());
        verify(store, never()).loadSlots(any(), any());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void journalInsertFailureReturnsStorageErrorWithoutSlotMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-journal-fail", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.findOperation(operationId)).thenReturn(Optional.empty());
        when(store.insertPendingOperation(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        4,
                        "facility-1",
                        payload))
                .thenReturn(false);

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void depositDeniedWhenFacilityAccessFailsDoesNotTouchPolicy() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(facilityAccess.validateMutationAccess(memberId, guildId, "foreign-facility"))
                .thenReturn(StorageResult.failure(
                        StorageResult.Status.PERMISSION_DENIED, "Storage facility is not governed by guild"));

        storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                2,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "foreign-facility");

        verify(store, never()).loadPolicy(guildId);
    }


    @Test
    void reconcilePendingDepositFinalizesCommittedWhenSlotAndAuditEvidenceMatch() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-reconcile", "payload");
        StorageSlot slot = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                "facility-1",
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(4, slot));
        when(store.hasMatchingAudit(
                        guildId,
                        memberId,
                        "DEPOSIT",
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        4,
                        "facility-1",
                        createdAt))
                .thenReturn(true);

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.COMMITTED),
                        eq(StorageResult.Status.SUCCESS.name()),
                        isNull(),
                        eq(slot),
                        eq(payload));
    }

    @Test
    void reconcilePendingDepositMarksCompensatedWhenEvidenceMissing() {
        UUID operationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                "facility-1",
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.hasMatchingAudit(
                        guildId,
                        memberId,
                        "DEPOSIT",
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        4,
                        "facility-1",
                        createdAt))
                .thenReturn(false);

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.COMPENSATED),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending deposit interrupted before durable slot and audit mutation"),
                        isNull(),
                        isNull());
    }

    @Test
    void duplicateOperationSkipsPreconditionChecksBeforeReplay() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-dup", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(store.findOperation(operationId))
                .thenReturn(Optional.of(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        StorageOperationStatus.COMMITTED,
                        StorageResult.Status.SUCCESS.name(),
                        null,
                        payload,
                        saved,
                        Instant.parse("2026-08-21T12:00:00Z"),
                        Instant.parse("2026-08-21T12:00:00Z"))));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                payload,
                "facility-1");

        assertTrue(result.isSuccess());
        verify(store, never()).loadSlots(any(), any());
        verify(store, never()).loadPolicy(guildId);
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void mismatchedOperationIdentityReturnsConflictWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload stored = new OpaqueItemPayload("paper-bytes-v1", "fp-stored", "stored");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, stored, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(store.findOperation(operationId))
                .thenReturn(Optional.of(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        StorageOperationStatus.COMMITTED,
                        StorageResult.Status.SUCCESS.name(),
                        null,
                        stored,
                        saved,
                        Instant.parse("2026-08-21T12:00:00Z"),
                        Instant.parse("2026-08-21T12:00:00Z"))));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                2,
                stored,
                "facility-1");

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertEquals("Storage operation identity mismatch", result.errorMessage());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void reconcilePendingWithdrawFinalizesCommittedWhenAuditAndPayloadSnapshotPresent() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-withdraw", "payload");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "WITHDRAW",
                mayorId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                6,
                "facility-1",
                StorageOperationStatus.PENDING,
                null,
                null,
                payload,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.hasMatchingAudit(
                        guildId,
                        mayorId,
                        "WITHDRAW",
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        6,
                        "facility-1",
                        createdAt))
                .thenReturn(true);

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.COMMITTED),
                        eq(StorageResult.Status.SUCCESS.name()),
                        isNull(),
                        isNull(),
                        eq(payload));
    }


    private Resident member(String name, UUID uuid) {
        Resident resident = new Resident(uuid, name);
        resident.setGuild(guild.getName());
        return resident;
    }
}
