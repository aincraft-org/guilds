package com.azoth.territory.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageContractTest {
    @Test
    void opaquePayloadRequiresNonBlankFieldsAndTrimsIdentifiers() {
        OpaqueItemPayload payload = new OpaqueItemPayload(
                "  minecraft:item  ", " {\"id\":\"minecraft:item\"} ", "  sha256:abc  ");

        assertEquals("minecraft:item", payload.schema());
        assertEquals(" {\"id\":\"minecraft:item\"} ", payload.payloadJson());
        assertEquals("sha256:abc", payload.fingerprint());
        assertThrows(NullPointerException.class, () -> new OpaqueItemPayload(null, "{}", "fp"));
        assertThrows(IllegalArgumentException.class, () -> new OpaqueItemPayload("  ", "{}", "fp"));
        assertThrows(NullPointerException.class, () -> new OpaqueItemPayload("schema", null, "fp"));
        assertThrows(IllegalArgumentException.class, () -> new OpaqueItemPayload("schema", "\n", "fp"));
        assertThrows(NullPointerException.class, () -> new OpaqueItemPayload("schema", "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new OpaqueItemPayload("schema", "{}", "\t"));
    }

    @Test
    void addressTrimsIdentifiersAndRejectsNegativeSlots() {
        StorageAddress address = new StorageAddress("  guild-1 ", " tab-a ", 0);

        assertEquals("guild-1", address.guildId());
        assertEquals("tab-a", address.tabId());
        assertThrows(NullPointerException.class, () -> new StorageAddress(null, "tab", 0));
        assertThrows(IllegalArgumentException.class, () -> new StorageAddress(" ", "tab", 0));
        assertThrows(NullPointerException.class, () -> new StorageAddress("guild", null, 0));
        assertThrows(IllegalArgumentException.class, () -> new StorageAddress("guild", "\n", 0));
        assertThrows(IllegalArgumentException.class, () -> new StorageAddress("guild", "tab", -1));
    }

    @Test
    void tabRejectsBlankIdsAndNonPositiveCapacity() {
        StorageTab tab = new StorageTab("  main ", "Main", 0, 9, true);

        assertEquals("main", tab.id());
        assertThrows(NullPointerException.class, () -> new StorageTab(null, "Main", 0, 9, true));
        assertThrows(IllegalArgumentException.class, () -> new StorageTab(" ", "Main", 0, 9, true));
        assertThrows(IllegalArgumentException.class, () -> new StorageTab("main", "Main", 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new StorageTab("main", "Main", 0, -1, true));
    }

    @Test
    void policyRequiresRanksAndDefaultsUseExpectedThresholds() {
        assertEquals(StorageRank.MEMBER, GuildStoragePolicy.defaults().depositRank());
        assertEquals(StorageRank.ASSISTANT, GuildStoragePolicy.defaults().withdrawRank());
        assertEquals(StorageRank.MAYOR, GuildStoragePolicy.defaults().manageRank());
        assertThrows(NullPointerException.class,
                () -> new GuildStoragePolicy(null, StorageRank.ASSISTANT, StorageRank.MAYOR));
        assertThrows(NullPointerException.class,
                () -> new GuildStoragePolicy(StorageRank.MEMBER, null, StorageRank.MAYOR));
        assertThrows(NullPointerException.class,
                () -> new GuildStoragePolicy(StorageRank.MEMBER, StorageRank.ASSISTANT, null));
    }

    @Test
    void snapshotCopiesCollectionsAndRejectsDuplicateTabIds() {
        StorageTab main = new StorageTab("main", "Main", 0, 9, true);
        StorageAddress address = new StorageAddress("guild", "main", 0);
        OpaqueItemPayload item = new OpaqueItemPayload("schema", "{}", "fingerprint");
        List<StorageTab> tabs = new ArrayList<>(List.of(main));
        Map<StorageAddress, OpaqueItemPayload> occupied = new HashMap<>(Map.of(address, item));

        GuildStorageSnapshot snapshot = new GuildStorageSnapshot(
                "  guild ", tabs, occupied, GuildStoragePolicy.defaults());
        tabs.clear();
        occupied.clear();

        assertEquals("guild", snapshot.guildId());
        assertEquals(List.of(main), snapshot.tabs());
        assertEquals(Map.of(address, item), snapshot.occupiedSlots());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.tabs().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.occupiedSlots().clear());
        assertThrows(IllegalArgumentException.class, () -> new GuildStorageSnapshot(
                "guild",
                List.of(main, new StorageTab(" main ", "Other", 1, 9, false)),
                Map.of(),
                GuildStoragePolicy.defaults()));
    }

    @Test
    void resultWrappersRequireSuccessPayloadsOnlyOnSuccess() {
        assertThrows(NullPointerException.class, () -> new StorageResult(null, "message"));
        assertThrows(NullPointerException.class, () -> new StorageOpenResult(null, "message", Optional.empty()));
        assertThrows(NullPointerException.class, () -> new StorageWithdrawResult(null, "message", Optional.empty()));

        GuildStorageSnapshot snapshot = new GuildStorageSnapshot(
                "guild", List.of(), Map.of(), GuildStoragePolicy.defaults());
        OpaqueItemPayload payload = new OpaqueItemPayload("schema", "{}", "fingerprint");
        StorageOpenResult open = new StorageOpenResult(StorageStatus.SUCCESS, "opened", Optional.of(snapshot));
        StorageWithdrawResult withdraw = new StorageWithdrawResult(
                StorageStatus.SUCCESS, "withdrawn", Optional.of(payload));
        assertEquals(Optional.of(snapshot), open.snapshot());
        assertEquals(Optional.of(payload), withdraw.payload());

        assertThrows(IllegalArgumentException.class,
                () -> new StorageOpenResult(StorageStatus.NOT_RESIDENT, "denied", Optional.of(snapshot)));
        assertThrows(IllegalArgumentException.class,
                () -> new StorageWithdrawResult(StorageStatus.CONFLICT, "denied", Optional.of(payload)));
        StorageOpenResult deniedOpen = new StorageOpenResult(
                StorageStatus.NOT_RESIDENT, "denied", Optional.empty());
        StorageWithdrawResult deniedWithdraw = new StorageWithdrawResult(
                StorageStatus.CONFLICT, "denied", Optional.empty());
        assertTrue(deniedOpen.snapshot().isEmpty());
        assertTrue(deniedWithdraw.payload().isEmpty());
    }
}
