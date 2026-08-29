package org.aincraft.guilds.territory.registry;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityRegistryTest {

    private static Territory territory(String id) {
        return new Territory(id, id, "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
    }

    private static Territory territoryAt(String id, int offset) {
        return new Territory(id, id, "world", Boundary.ofPolygon(List.of(
                new BlockPos(offset, 0), new BlockPos(offset + 100, 0),
                new BlockPos(offset + 100, 100), new BlockPos(offset, 100))));
    }

    private static SettlementFacility facility(String id, FacilityType type, int x, int z) {
        return new SettlementFacility(id, id, "t1", type, "world", x, 64, z);
    }

    @Test
    void resolvesRegisteredFacilityByBlockLocation() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 5, 5);

        facilities.register(market);

        assertEquals(Optional.of(market), facilities.resolve("world", 5, 64, 5));
        assertEquals(Optional.of(market), facilities.get("market"));
        assertEquals(List.of(market), facilities.list());
    }

    @Test
    void rejectsUnknownTerritoryAndOutsideLocation() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));

        assertThrows(IllegalArgumentException.class, () -> facilities.register(
                new SettlementFacility("unknown", "Unknown", "missing", FacilityType.STORAGE,
                        "world", 5, 64, 5)));
        assertThrows(IllegalArgumentException.class, () -> facilities.register(
                new SettlementFacility("outside", "Outside", "t1", FacilityType.STORAGE,
                        "world", 500, 64, 500)));
    }

    @Test
    void rejectsDuplicateIdsAndLocations() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        facilities.register(facility("market", FacilityType.TRADING_POST, 5, 5));

        assertThrows(IllegalArgumentException.class, () -> facilities.register(
                facility("market", FacilityType.STORAGE, 6, 6)));
        assertThrows(IllegalArgumentException.class, () -> facilities.register(
                facility("warehouse", FacilityType.STORAGE, 5, 5)));
    }

    @Test
    void replaceAllIsAtomicAndUnregisterRemovesFacility() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 5, 5);
        facilities.register(market);

        assertThrows(IllegalArgumentException.class, () -> facilities.replaceAll(List.of(
                market, facility("bad", FacilityType.STORAGE, 500, 500))));
        assertEquals(List.of(market), facilities.list());

        assertTrue(facilities.unregister("market"));
        assertFalse(facilities.unregister("market"));
        assertTrue(facilities.list().isEmpty());
    }

    @Test
    void differentFacilityTypesCanUseDifferentLocations() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 5, 5);
        SettlementFacility warehouse = facility("warehouse", FacilityType.STORAGE, 6, 6);

        facilities.replaceAll(List.of(market, warehouse));

        assertEquals(2, facilities.list().size());
    }

    @Test
    void filtersFacilitiesByTerritoryAndTypeInRegistrationOrder() {
        FacilityRegistry facilities = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"), territoryAt("t2", 200))));
        SettlementFacility first = facility("first", FacilityType.WAYSTONE, 5, 5);
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 6, 6);
        SettlementFacility second = facility("second", FacilityType.WAYSTONE, 7, 7);
        facilities.replaceAll(List.of(first, market, second));

        assertEquals(List.of(first, second), facilities.list("t1", FacilityType.WAYSTONE));
        assertTrue(facilities.list("t2", FacilityType.WAYSTONE).isEmpty());
    }

    @Test
    void copyCanMutateWithoutChangingLiveRegistry() {
        FacilityRegistry live = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        live.register(facility("market", FacilityType.TRADING_POST, 5, 5));

        FacilityRegistry candidate = live.copy();
        candidate.register(facility("stone", FacilityType.WAYSTONE, 6, 6));

        assertEquals(List.of("market"),
                live.list().stream().map(SettlementFacility::id).toList());
        assertEquals(List.of("market", "stone"),
                candidate.list().stream().map(SettlementFacility::id).toList());
    }
    @Test
    void countIncludesEveryPersistedRecordInCandidateRegistry() {
        FacilityRegistry live = new FacilityRegistry(
                new TerritoryRegistry(List.of(territory("t1"))));
        SettlementFacility first = facility("boat-active", FacilityType.BOAT, 5, 5);
        SettlementFacility inactive = facility("boat-inactive", FacilityType.BOAT, 6, 6);
        live.replaceAll(List.of(first, inactive));

        FacilityRegistry candidate = live.copy();

        assertEquals(2, live.count("t1", FacilityType.BOAT));
        assertEquals(2, candidate.count("t1", FacilityType.BOAT));
        assertEquals(0, candidate.count("t1", FacilityType.AIRSHIP));
    }
}
