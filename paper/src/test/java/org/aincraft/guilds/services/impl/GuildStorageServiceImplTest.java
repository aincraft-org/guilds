package org.aincraft.guilds.services.impl;

import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.persist.PostgresGuildStorageStore;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.storage.GuildStoragePolicy;
import com.azoth.territory.storage.GuildStorageSnapshot;
import com.azoth.territory.storage.OpaqueItemPayload;
import com.azoth.territory.storage.StorageAddress;
import com.azoth.territory.storage.StorageOpenResult;
import com.azoth.territory.storage.StorageRank;
import com.azoth.territory.storage.StorageResult;
import com.azoth.territory.storage.StorageStatus;
import com.azoth.territory.storage.StorageWithdrawResult;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.GuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuildStorageServiceImplTest {
    private static final String GUILD_ID = "guild-a";
    private static final String FACILITY_ID = "storage-a";
    private static final String WORLD = "world";
    private static final int X = 10;
    private static final int Y = 64;
    private static final int Z = -4;

    private final UUID mayor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    private GuildService guildService;
    private FacilityRegistry facilityRegistry;
    private GovernanceRegistry governanceRegistry;
    private PostgresGuildStorageStore store;
    private GuildStorageServiceImpl service;
    private Guild guild;
    private SettlementFacility facility;
    private GuildBody governingBody;
    private StorageAddress address;
    private OpaqueItemPayload payload;

    @BeforeEach
    void setUp() {
        guildService = mock(GuildService.class);
        facilityRegistry = mock(FacilityRegistry.class);
        governanceRegistry = mock(GovernanceRegistry.class);
        store = mock(PostgresGuildStorageStore.class);
        service = new GuildStorageServiceImpl(guildService, facilityRegistry, governanceRegistry, store);

        guild = new Guild("Guild A", mayor);
        guild.setId(GUILD_ID);
        guild.addResident(assistant);
        guild.addResident(member);
        guild.addAssistant(assistant);
        when(guildService.getAllGuilds()).thenReturn(List.of(guild));

        facility = new SettlementFacility(
                FACILITY_ID, "Storage", "territory-a", FacilityType.STORAGE, WORLD, X, Y, Z);
        when(facilityRegistry.resolve(WORLD, X, Y, Z)).thenReturn(Optional.of(facility));
        governingBody = mock(GuildBody.class);
        when(governingBody.id()).thenReturn(GUILD_ID);
        when(governanceRegistry.governingGuildForTerritory("territory-a"))
                .thenReturn(Optional.of(governingBody));

        address = new StorageAddress(GUILD_ID, "general", 0);
        payload = new OpaqueItemPayload("paper-item", "{}", "fingerprint");
    }

    @Test
    void residentAtStorageFacilityCanOpenAndDeposit() throws IOException {
        GuildStorageSnapshot snapshot = snapshot(GuildStoragePolicy.defaults());
        when(store.ensureBank(GUILD_ID)).thenReturn(snapshot);
        when(store.load(GUILD_ID)).thenReturn(snapshot);
        when(store.put(GUILD_ID, address, payload, member, FACILITY_ID))
                .thenReturn(new StorageResult(StorageStatus.SUCCESS, "stored"));

        StorageOpenResult opened = service.open(member, WORLD, X, Y, Z);
        StorageResult deposited = service.deposit(
                member, address, payload, FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.SUCCESS, opened.status());
        assertTrue(opened.snapshot().isPresent());
        assertEquals(StorageStatus.SUCCESS, deposited.status());
        verify(store).put(GUILD_ID, address, payload, member, FACILITY_ID);
    }

    @Test
    void nonResidentIsDeniedBeforeStoreAccess() throws IOException {
        StorageResult result = service.deposit(
                outsider, address, payload, FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.NOT_RESIDENT, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void allianceMemberWithDifferentGuildIsDenied() throws IOException {
        Guild alliedGuild = new Guild("Allied", outsider);
        alliedGuild.setId("guild-b");
        when(guildService.getAllGuilds()).thenReturn(List.of(alliedGuild));

        StorageOpenResult result = service.open(outsider, WORLD, X, Y, Z);

        assertEquals(StorageStatus.WRONG_GUILD, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void tradingPostFacilityIsDenied() throws IOException {
        SettlementFacility tradingPost = new SettlementFacility(
                FACILITY_ID, "Trading", "territory-a", FacilityType.TRADING_POST, WORLD, X, Y, Z);
        when(facilityRegistry.resolve(WORLD, X, Y, Z)).thenReturn(Optional.of(tradingPost));

        StorageOpenResult result = service.open(member, WORLD, X, Y, Z);

        assertEquals(StorageStatus.WRONG_FACILITY, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void facilityGovernedByAnotherGuildIsDenied() throws IOException {
        when(governingBody.id()).thenReturn("guild-b");

        StorageOpenResult result = service.open(member, WORLD, X, Y, Z);

        assertEquals(StorageStatus.WRONG_GUILD, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void wrongWorldOrBlockIsDenied() throws IOException {
        when(facilityRegistry.resolve("nether", X, Y, Z)).thenReturn(Optional.empty());

        StorageOpenResult result = service.open(member, "nether", X, Y, Z);

        assertEquals(StorageStatus.WRONG_FACILITY, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void memberCannotWithdrawOrManageByDefault() throws IOException {
        when(store.load(GUILD_ID)).thenReturn(snapshot(GuildStoragePolicy.defaults()));

        StorageWithdrawResult withdrawn = service.withdraw(member, address, FACILITY_ID, WORLD, X, Y, Z);
        StorageResult managed = service.setPolicy(
                member, GUILD_ID, GuildStoragePolicy.defaults(), FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.INSUFFICIENT_RANK, withdrawn.status());
        assertEquals(StorageStatus.INSUFFICIENT_RANK, managed.status());
        verify(store, never()).remove(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(store, never()).setPolicy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assistantCanWithdrawButCannotManage() throws IOException {
        when(store.load(GUILD_ID)).thenReturn(snapshot(GuildStoragePolicy.defaults()));
        StorageWithdrawResult expected = new StorageWithdrawResult(
                StorageStatus.SUCCESS, "withdrawn", Optional.of(payload));
        when(store.remove(GUILD_ID, address, assistant, FACILITY_ID)).thenReturn(expected);

        StorageWithdrawResult withdrawn = service.withdraw(assistant, address, FACILITY_ID, WORLD, X, Y, Z);
        StorageResult managed = service.setPolicy(
                assistant, GUILD_ID, GuildStoragePolicy.defaults(), FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.SUCCESS, withdrawn.status());
        assertEquals(StorageStatus.INSUFFICIENT_RANK, managed.status());
        verify(store).remove(GUILD_ID, address, assistant, FACILITY_ID);
        verify(store, never()).setPolicy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mayorCanManagePolicyAndTabs() throws IOException {
        when(store.load(GUILD_ID)).thenReturn(snapshot(GuildStoragePolicy.defaults()));
        when(store.setPolicy(GUILD_ID, GuildStoragePolicy.defaults(), mayor, FACILITY_ID))
                .thenReturn(new StorageResult(StorageStatus.SUCCESS, "updated"));
        when(store.unlockTab(GUILD_ID, "rare", "Rare", 1, 9, mayor, FACILITY_ID))
                .thenReturn(new StorageResult(StorageStatus.SUCCESS, "unlocked"));

        StorageResult policy = service.setPolicy(
                mayor, GUILD_ID, GuildStoragePolicy.defaults(), FACILITY_ID, WORLD, X, Y, Z);
        StorageResult tab = service.unlockTab(
                mayor, GUILD_ID, "rare", "Rare", 1, 9, FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.SUCCESS, policy.status());
        assertEquals(StorageStatus.SUCCESS, tab.status());
        verify(store).setPolicy(GUILD_ID, GuildStoragePolicy.defaults(), mayor, FACILITY_ID);
        verify(store).unlockTab(GUILD_ID, "rare", "Rare", 1, 9, mayor, FACILITY_ID);
    }

    @Test
    void changedPolicyThresholdsAreHonored() throws IOException {
        GuildStoragePolicy restricted = new GuildStoragePolicy(
                StorageRank.MAYOR, StorageRank.MEMBER, StorageRank.MAYOR);
        when(store.load(GUILD_ID)).thenReturn(snapshot(restricted));
        when(store.put(GUILD_ID, address, payload, member, FACILITY_ID))
                .thenReturn(new StorageResult(StorageStatus.SUCCESS, "stored"));
        when(store.put(GUILD_ID, address, payload, mayor, FACILITY_ID))
                .thenReturn(new StorageResult(StorageStatus.SUCCESS, "stored"));

        StorageResult memberDeposit = service.deposit(
                member, address, payload, FACILITY_ID, WORLD, X, Y, Z);
        StorageResult mayorDeposit = service.deposit(
                mayor, address, payload, FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.INSUFFICIENT_RANK, memberDeposit.status());
        assertEquals(StorageStatus.SUCCESS, mayorDeposit.status());
        verify(store).put(GUILD_ID, address, payload, mayor, FACILITY_ID);
    }

    @Test
    void storeIOExceptionReturnsStorageErrorWithoutRetryOrMutation() throws IOException {
        when(store.load(GUILD_ID)).thenReturn(snapshot(GuildStoragePolicy.defaults()));
        when(store.put(GUILD_ID, address, payload, member, FACILITY_ID))
                .thenThrow(new IOException("database unavailable"));

        StorageResult result = service.deposit(
                member, address, payload, FACILITY_ID, WORLD, X, Y, Z);

        assertEquals(StorageStatus.STORAGE_ERROR, result.status());
        verify(store).put(GUILD_ID, address, payload, member, FACILITY_ID);
        verify(store, never()).remove(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrongFacilityIdIsDeniedBeforeStoreAccess() throws IOException {
        StorageResult result = service.deposit(
                member, address, payload, "other-storage", WORLD, X, Y, Z);

        assertEquals(StorageStatus.WRONG_FACILITY, result.status());
        verifyNoInteractions(store);
    }

    @Test
    void openStoreIOExceptionReturnsStorageErrorWithoutSnapshot() throws IOException {
        when(store.ensureBank(GUILD_ID)).thenThrow(new IOException("database unavailable"));

        StorageOpenResult result = service.open(member, WORLD, X, Y, Z);

        assertEquals(StorageStatus.STORAGE_ERROR, result.status());
        assertTrue(result.snapshot().isEmpty());
        verify(store).ensureBank(GUILD_ID);
    }

    private GuildStorageSnapshot snapshot(GuildStoragePolicy policy) {
        return new GuildStorageSnapshot(GUILD_ID, List.of(), Map.of(), policy);
    }
}
