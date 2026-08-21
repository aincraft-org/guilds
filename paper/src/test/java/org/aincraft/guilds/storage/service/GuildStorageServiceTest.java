package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.storage.persist.SqlGuildStorageStore;
import org.aincraft.guilds.storage.service.impl.GuildStorageServiceImpl;
import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private GuildStorageServiceImpl storageService;
    private String guildId;
    private UUID mayorId;
    private UUID memberId;
    private Guild guild;

    @BeforeEach
    void setUp() {
        storageService = new GuildStorageServiceImpl(
                store, guildService, residentService, Runnable::run, Runnable::run);
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
    }

    @Test
    void depositByMemberSucceedsAndRecordsAudit() {
        Resident member = member("Member", memberId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-deposit", "payload");
        StorageSlot saved = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));

        when(residentService.getResident(memberId)).thenReturn(Optional.of(member));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID))
                .thenReturn(Map.of())
                .thenReturn(Map.of(4, saved))
                .thenReturn(Map.of(4, saved));
        when(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 4, payload, 0L)).thenReturn(true);

        StorageResult<StorageSlot> result = storageService.deposit(
                memberId,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                4,
                payload,
                "facility-1");

        assertTrue(result.isSuccess(), () -> result.status() + ": " + result.errorMessage());
        assertEquals(payload, result.value().orElseThrow().item());
        ArgumentCaptor<UUID> actorCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(store).recordAudit(
                eq(guildId),
                actorCaptor.capture(),
                eq("DEPOSIT"),
                eq(SqlGuildStorageStore.DEFAULT_TAB_ID),
                eq(4),
                eq("fp-deposit"),
                org.mockito.ArgumentMatchers.startsWith("facility-1:"));
        assertEquals(memberId, actorCaptor.getValue());
    }

    @Test
    void depositByOutsiderIsUnauthorized() {
        UUID outsider = UUID.randomUUID();
        when(residentService.getResident(outsider)).thenReturn(Optional.of(member("Outsider", outsider)));

        StorageResult<StorageSlot> result = storageService.deposit(
                outsider,
                guildId,
                SqlGuildStorageStore.DEFAULT_TAB_ID,
                1,
                new OpaqueItemPayload("paper-bytes-v1", "fp", "payload"),
                "facility-1");

        assertEquals(StorageResult.Status.UNAUTHORIZED, result.status());
        verify(store, never()).saveSlot(any(), any(), anyInt(), any(), anyLong());
    }

    @Test
    void withdrawByMemberIsPermissionDenied() {
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp", "payload");
        StorageSlot occupied = new StorageSlot(
                guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, payload, 1L, Instant.parse("2026-08-21T12:00:00Z"));
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member("Member", memberId)));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of(2, occupied));

        StorageResult<OpaqueItemPayload> result = storageService.withdraw(
                memberId, guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 2, "facility-1");

        assertEquals(StorageResult.Status.PERMISSION_DENIED, result.status());
        verify(store, never()).saveSlot(any(), any(), anyInt(), isNull(), anyLong());
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
                mayorId, guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 8, "facility-1");

        assertEquals(StorageResult.Status.SLOT_EMPTY, result.status());
    }

    @Test
    void depositRunsCompensationOnMainThreadWhenSqlPersistFails() {
        Resident member = member("Member", memberId);
        OpaqueItemPayload payload = new OpaqueItemPayload("paper-bytes-v1", "fp-comp", "payload");
        when(residentService.getResident(memberId)).thenReturn(Optional.of(member));
        when(store.loadSlots(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID)).thenReturn(Map.of());
        when(store.saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 5, payload, 0L)).thenReturn(false);
        AtomicBoolean compensated = new AtomicBoolean();
        AtomicReference<Thread> compensationThread = new AtomicReference<>();
        GuildStorageServiceImpl service = new GuildStorageServiceImpl(
                store,
                guildService,
                residentService,
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
        doReturn(false)
                .when(store)
                .saveSlot(guildId, SqlGuildStorageStore.DEFAULT_TAB_ID, 9, null, 3L);
        AtomicBoolean compensated = new AtomicBoolean();
        GuildStorageServiceImpl service = new GuildStorageServiceImpl(
                store,
                guildService,
                residentService,
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

    private Resident member(String name, UUID uuid) {
        Resident resident = new Resident(uuid, name);
        resident.setGuild(guild.getName());
        return resident;
    }
}
