package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.persist.AuditEvidenceLookupResult;
import org.aincraft.guilds.storage.persist.StorageOperationLookupResult;
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
        when(store.lookupOperation(any())).thenReturn(StorageOperationLookupResult.notFound());
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
        when(store.depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(4), eq(payload), eq(memberId), eq("facility-1"), any()))
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
        verify(store).depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(4), eq(payload), eq(memberId), eq("facility-1"), any());
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
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
        verify(store, never()).withdrawWithAudit(any(), any(), anyInt(), any(), anyLong(), any(), any(), any());
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
        when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        payload,
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
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
                payload,
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
        when(store.lookupOperation(operationId)).thenAnswer(invocation -> {
            if (findCalls.incrementAndGet() == 1) {
                return StorageOperationLookupResult.notFound();
            }
            return StorageOperationLookupResult.found(pending);
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
        when(store.depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(3), eq(payload), eq(memberId), eq("facility-1"), any()))
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
            verify(store, org.mockito.Mockito.times(1)).depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(3), eq(payload), eq(memberId), eq("facility-1"), eq(operationId));
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
                Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        payload,
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void depositRunsCompensationOnMainThreadWhenSqlPersistFails() {
        Resident member = member("Member", memberId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-comp", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(5), eq(payload), eq(memberId), eq("facility-1"), any()))
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
        when(store.withdrawWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(9), eq(payload), eq(3L), eq(mayorId), eq("facility-1"), any()))
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void journalInsertFailureReturnsStorageErrorWithoutSlotMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-journal-fail", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.notFound());
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
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
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(4, slot));
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(memberId),
                        eq("DEPOSIT"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(4),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.matching());

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
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-missing-audit", "payload");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                "facility-1",
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(memberId),
                        eq("DEPOSIT"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(4),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.none());

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
    void reconcilePendingDepositPreservesUnknownWhenSlotContentsMismatch() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload request = new OpaqueItemPayload("paper-bytes-v1", "fp-request", "request");
        OpaqueItemPayload slotItem = new OpaqueItemPayload("paper-bytes-v1", "fp-slot", "slot");
        StorageSlot slot = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, slotItem, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                "facility-1",
                request,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(4, slot));
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(memberId),
                        eq("DEPOSIT"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(4),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(request)))
                .thenReturn(AuditEvidenceLookupResult.matching());

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending deposit slot contents do not match request snapshot; operator reconciliation required"),
                        isNull(),
                        isNull());
    }

    @Test
    void reconcilePendingDepositPreservesUnknownWhenAuditWithoutSlot() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-audit-only", "payload");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                "facility-1",
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(memberId),
                        eq("DEPOSIT"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(4),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.matching());

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending deposit audit recorded without slot contents; operator reconciliation required"),
                        isNull(),
                        isNull());
    }

    @Test
    void reconcilePendingWithdrawPreservesUnknownWhenAuditAndSlotOccupied() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-withdraw-audit", "payload");
        StorageSlot slot = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 6, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "WITHDRAW",
                mayorId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                6,
                "facility-1",
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(6, slot));
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(mayorId),
                        eq("WITHDRAW"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(6),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.matching());

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending withdraw audit recorded while slot still occupied; operator reconciliation required"),
                        isNull(),
                        isNull());
    }


    @Test
    void reconcilePendingDepositMarksCompensatedWhenRequestSnapshotMissing() {
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
                null,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.COMPENSATED),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending deposit missing request snapshot; operator reconciliation required"),
                        isNull(),
                        isNull());
        verify(store, never()).lookupMatchingAudit(any(), any(), any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void reconcilePendingWithdrawMarksCompensatedWhenRequestSnapshotMissing() {
        UUID operationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "WITHDRAW",
                mayorId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                6,
                "facility-1",
                null,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.COMPENSATED),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Pending withdraw missing request snapshot; operator reconciliation required"),
                        isNull(),
                        isNull());
        verify(store, never()).lookupMatchingAudit(any(), any(), any(), any(), any(), anyInt(), any(), any(), any());
    }
    @Test
    void duplicateOperationSkipsPreconditionChecksBeforeReplay() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-dup", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
                Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        payload,
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void mismatchedOperationIdentityReturnsConflictWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload stored = new OpaqueItemPayload("paper-bytes-v1", "fp-stored", "stored");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 1, stored, 1L, Instant.parse("2026-08-21T12:00:00Z"));
                Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        1,
                        "facility-1",
                        stored,
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
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void mismatchedWithdrawSnapshotReturnsConflictWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload pendingItem = new OpaqueItemPayload("paper-bytes-v1", "fp-pending", "pending");
        OpaqueItemPayload slotItem = new OpaqueItemPayload("paper-bytes-v1", "fp-slot", "slot");
        StorageSlot occupied = new StorageSlot(
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                8,
                slotItem,
                1L,
                Instant.parse("2026-08-21T12:00:00Z"));
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "WITHDRAW",
                mayorId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                8,
                "facility-1",
                pendingItem,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(residentService.getResident(mayorId)).thenReturn(Optional.of(member("Mayor", mayorId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(8, occupied));
        when(store.lookupOperation(operationId)).thenReturn(
                StorageOperationLookupResult.notFound(),
                StorageOperationLookupResult.found(pending));
        when(store.insertPendingOperation(
                        operationId,
                        guildId,
                        "WITHDRAW",
                        mayorId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        8,
                        "facility-1",
                        slotItem))
                .thenReturn(false);

        StorageResult<OpaqueItemPayload> result = storageService.withdraw(
                operationId,
                mayorId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                8,
                "facility-1");

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertEquals("Storage operation identity mismatch", result.errorMessage());
        verify(store, never()).withdrawWithAudit(any(), any(), anyInt(), any(), anyLong(), any(), any(), any());
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
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(mayorId),
                        eq("WITHDRAW"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(6),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.matching());

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

    @Test
    void finalizeFailureAfterSuccessfulMutationPreservesUnknownOutcome() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-unknown", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 20, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(20), eq(payload), eq(memberId), eq("facility-1"), eq(operationId)))
                .thenReturn(new SqlGuildStorageStore.DepositAuditOutcome(
                        SqlGuildStorageStore.SlotMutationResult.SUCCESS, saved));
        org.mockito.Mockito.doAnswer(invocation -> {
                    if (invocation.getArgument(1) == StorageOperationStatus.COMMITTED) {
                        throw new IllegalStateException("simulated finalize failure");
                    }
                    return null;
                })
                .when(store)
                .finalizeOperation(any(), any(), any(), any(), any(), any());

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                20,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        assertEquals(
                "Storage mutation outcome unknown; retry with same operationId", result.errorMessage());
        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Storage mutation succeeded but journal finalize failed"),
                        isNull(),
                        isNull());
    }

    @Test
    void journalLookupFailureAfterInsertConflictReturnsStorageError() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-journal-lookup", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.insertPendingOperation(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        21,
                        "facility-1",
                        payload))
                .thenReturn(false);
        when(store.lookupOperation(operationId))
                .thenReturn(
                        StorageOperationLookupResult.notFound(),
                        StorageOperationLookupResult.readFailure("journal read failed"));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                21,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        assertEquals(
                "Failed to load storage operation journal after insert conflict", result.errorMessage());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void duplicateDepositWithDifferentPayloadReturnsConflictWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload stored = new OpaqueItemPayload("paper-bytes-v1", "fp-stored", "stored-bytes");
        OpaqueItemPayload different = new OpaqueItemPayload("paper-bytes-v1", "fp-other", "other-bytes");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 22, stored, 1L, Instant.parse("2026-08-21T12:00:00Z"));
                Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        22,
                        "facility-1",
                        stored,
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
                22,
                different,
                "facility-1");

        assertEquals(StorageResult.Status.CONFLICT, result.status());
        assertEquals("Storage operation identity mismatch", result.errorMessage());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void unknownOperationReplayReturnsStorageErrorWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-unknown-replay", "payload");
                Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
when(store.lookupOperation(operationId)).thenReturn(StorageOperationLookupResult.found(new StorageOperationRecord(
                        operationId,
                        guildId,
                        "DEPOSIT",
                        memberId,
                        SqlGuildStorageStore.DEFAULT_TAB_ID,
                        23,
                        "facility-1",
                        payload,
                        StorageOperationStatus.UNKNOWN,
                        StorageResult.Status.STORAGE_ERROR.name(),
                        "Storage mutation interrupted; outcome unknown",
                        null,
                        null,
                        Instant.parse("2026-08-21T12:00:00Z"),
                        Instant.parse("2026-08-21T12:00:00Z"))));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                23,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        assertEquals("Storage mutation interrupted; outcome unknown", result.errorMessage());
        verify(store, never()).depositWithAudit(any(), any(), anyInt(), any(), any(), any(), any());
    }


    @Test
    void initialJournalLookupFailureReturnsStorageErrorWithoutMutation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-initial-lookup", "payload");
        when(store.lookupOperation(operationId))
                .thenReturn(StorageOperationLookupResult.readFailure("journal read failed"));
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                24,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        assertEquals("Failed to load storage operation journal", result.errorMessage());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void reconcilePendingDepositPreservesUnknownWhenAuditLookupFails() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-audit-fail", "payload");
        Instant createdAt = Instant.parse("2026-08-21T12:00:00Z");
        StorageOperationRecord pending = new StorageOperationRecord(
                operationId,
                guildId,
                "DEPOSIT",
                memberId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                5,
                "facility-1",
                payload,
                StorageOperationStatus.PENDING,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
        when(store.findPendingOperations()).thenReturn(List.of(pending));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.lookupMatchingAudit(
                        eq(operationId),
                        eq(guildId),
                        eq(memberId),
                        eq("DEPOSIT"),
                        eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                        eq(5),
                        eq("facility-1"),
                        eq(createdAt),
                        eq(payload)))
                .thenReturn(AuditEvidenceLookupResult.readFailure("audit read failed"));

        GuildStorageServiceImpl.withDirectExecutorsForUnitTests(
                store, guildService, residentService, facilityAccess);

        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("audit read failed"),
                        isNull(),
                        isNull());
    }

    @Test
    void ambiguousDepositCommitPreservesUnknownWithoutCompensation() {
        UUID operationId = UUID.randomUUID();
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-ambiguous", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.depositWithAudit(eq(guildId), eq(SqlGuildStorageStore.DEFAULT_TAB_ID), eq(25), eq(payload), eq(memberId), eq("facility-1"), eq(operationId)))
                .thenReturn(new SqlGuildStorageStore.DepositAuditOutcome(
                        SqlGuildStorageStore.SlotMutationResult.UNKNOWN, null));

        StorageResult<StorageSlot> result = storageService.deposit(
                operationId,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                25,
                payload,
                "facility-1");

        assertEquals(StorageResult.Status.STORAGE_ERROR, result.status());
        assertEquals(
                "Storage mutation outcome unknown; retry with same operationId", result.errorMessage());
        verify(store, never()).finalizeOperation(
                eq(operationId), eq(StorageOperationStatus.COMPENSATED), any(), any(), any(), any());
        verify(store)
                .finalizeOperation(
                        eq(operationId),
                        eq(StorageOperationStatus.UNKNOWN),
                        eq(StorageResult.Status.STORAGE_ERROR.name()),
                        eq("Storage mutation outcome unknown; retry with same operationId"),
                        isNull(),
                        isNull());
    }

    @Test
    void depositRejectsNullOperationIdBeforeJournalLookup() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));

        StorageResult<StorageSlot> result = storageService.deposit(
                null,
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "facility-1");

        assertEquals(StorageResult.Status.INVALID_ARGUMENT, result.status());
        assertEquals("operationId is required", result.errorMessage());
        verify(store, never()).lookupOperation(any());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void depositRejectsNullItemBeforeJournalLookup() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                null,
                "facility-1");

        assertEquals(StorageResult.Status.INVALID_ARGUMENT, result.status());
        assertEquals("item is required", result.errorMessage());
        verify(store, never()).lookupOperation(any());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void depositRejectsBlankFacilityIdBeforeJournalLookup() {
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));

        StorageResult<StorageSlot> result = storageService.deposit(
                UUID.randomUUID(),
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "   ");

        assertEquals(StorageResult.Status.INVALID_ARGUMENT, result.status());
        assertEquals("facilityId is required", result.errorMessage());
        verify(store, never()).lookupOperation(any());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void withdrawRejectsNullOperationIdBeforeJournalLookup() {
        when(residentService.getResident(mayorId)).thenReturn(Optional.of(member("Mayor", mayorId)));

        StorageResult<OpaqueItemPayload> result = storageService.withdraw(
                null,
                mayorId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                "facility-1");

        assertEquals(StorageResult.Status.INVALID_ARGUMENT, result.status());
        assertEquals("operationId is required", result.errorMessage());
        verify(store, never()).lookupOperation(any());
        verify(store, never()).insertPendingOperation(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }


    private Resident member(String name, UUID uuid) {
        Resident resident = new Resident(uuid, name);
        resident.setGuild(guild.getName());
        return resident;
    }
}
