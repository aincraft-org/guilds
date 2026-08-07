package com.azoth.territory.registry;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
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

    /**
     * A {@link FacilityRegistry} bound to a candidate {@link TerritoryRegistry}
     * rejects the persisted facility set when the mutation removes the
     * territory that hosts a facility — the validation used by the web API
     * before publishing territory mutations.
     */
    @Test
    void candidateRegistryRejectsFacilitiesWhoseTerritoryWasRemoved() {
        TerritoryRegistry live = new TerritoryRegistry(List.of(territory("t1")));
        FacilityRegistry facilities = new FacilityRegistry(live);
        facilities.register(facility("market", FacilityType.TRADING_POST, 5, 5));

        TerritoryRegistry candidate = new TerritoryRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> new FacilityRegistry(candidate).replaceAll(facilities.list()));
    }

    /**
     * The same validation rejects a relocation that moves the territory
     * boundary away from a hosted facility.
     */
    @Test
    void candidateRegistryRejectsFacilitiesOutsideRelocatedTerritory() {
        TerritoryRegistry live = new TerritoryRegistry(List.of(territory("t1")));
        FacilityRegistry facilities = new FacilityRegistry(live);
        facilities.register(facility("market", FacilityType.TRADING_POST, 5, 5));

        Territory moved = new Territory("t1", "t1", "world", Boundary.ofPolygon(List.of(
                new BlockPos(1000, 1000), new BlockPos(1100, 1000),
                new BlockPos(1100, 1100), new BlockPos(1000, 1100))));
        TerritoryRegistry candidate = new TerritoryRegistry(List.of(moved));
        assertThrows(IllegalArgumentException.class,
                () -> new FacilityRegistry(candidate).replaceAll(facilities.list()));
    }

    /**
     * Mutations that keep every facility inside its territory (adding an
     * unrelated territory) pass candidate validation unchanged.
     */
    @Test
    void candidateRegistryAcceptsMutationsThatKeepFacilitiesValid() {
        TerritoryRegistry live = new TerritoryRegistry(List.of(territory("t1")));
        FacilityRegistry facilities = new FacilityRegistry(live);
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 5, 5);
        facilities.register(market);

        Territory unrelated = new Territory("t2", "t2", "world", Boundary.ofPolygon(List.of(
                new BlockPos(1000, 1000), new BlockPos(1100, 1000),
                new BlockPos(1100, 1100), new BlockPos(1000, 1100))));
        TerritoryRegistry candidate = new TerritoryRegistry(List.of(territory("t1"), unrelated));
        FacilityRegistry validated = new FacilityRegistry(candidate);
        validated.replaceAll(facilities.list());
        assertEquals(List.of(market), validated.list());
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
}
