package org.aincraft.guilds.territory.model;

import org.aincraft.guilds.territory.persist.TerritoryJson;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attach governments to territories; resolve spatial path still works and exposes government.
 */
class GovernmentTerritoryTest {


    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @Test
    void resolve_returnsGovernmentFormAndSeats() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Territory monarchyLand = new Territory(
                "crownlands",
                "Crownlands",
                "world",
                square(0, 200),
                List.of(),
                ZoneType.WILDERNESS,
                Government.monarchy("player:monarch-1")
        );
        registry.register(monarchyLand);

        LookupResult r = registry.resolve("world", 50, 50);
        assertTrue(r.isContained());
        assertEquals("crownlands", r.territoryId().orElseThrow());
        assertEquals(GovernmentForm.MONARCHY, r.governmentForm().orElseThrow());
        assertEquals("player:monarch-1", r.government().orElseThrow().sovereignHolderId().orElseThrow());
        assertEquals(ZoneType.WILDERNESS, r.zoneType().orElseThrow());
    }

    @Test
    void allThreeForms_persistAndReloadWithSeats() throws Exception {
        TerritoryRegistry original = new TerritoryRegistry();
        // Place non-overlapping territories side by side
        original.register(new Territory(
                "mon-land", "Mon", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS,
                Government.monarchy("holder:king")
        ));
        original.register(new Territory(
                "oli-land", "Oli", "world", square(100, 200),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("holder:c1", "holder:c2", "holder:c3"))
        ));
        original.register(new Territory(
                "dem-land", "Dem", "world", square(200, 300),
                List.of(), ZoneType.WILDERNESS,
                Government.democracy(3, List.of("holder:r1", "holder:r2"), List.of(99L, 100L))
        ));

        // Resolve before save
        assertEquals(GovernmentForm.MONARCHY,
                original.resolve("world", 10, 10).governmentForm().orElseThrow());
        assertEquals(GovernmentForm.OLIGARCHY,
                original.resolve("world", 150, 150).governmentForm().orElseThrow());
        assertEquals(GovernmentForm.DEMOCRACY,
                original.resolve("world", 250, 250).governmentForm().orElseThrow());

        TerritoryJson json = new TerritoryJson();
        TerritoryRegistry reloaded = new TerritoryRegistry();
        reloaded.replaceAll(json.registryFromJson(json.registryToJson(original)));
        assertEquals(3, reloaded.size());

        Government mon = reloaded.get("mon-land").orElseThrow().government();
        assertEquals(GovernmentForm.MONARCHY, mon.form());
        assertEquals("holder:king", mon.sovereignHolderId().orElseThrow());
        assertEquals(1, mon.seatCount());
        assertEquals(SeatRole.SOVEREIGN, mon.seats().get(0).role());

        Government oli = reloaded.get("oli-land").orElseThrow().government();
        assertEquals(GovernmentForm.OLIGARCHY, oli.form());
        assertEquals(3, oli.seatCount());
        assertEquals(SeatRole.COUNCILOR, oli.seats().get(0).role());
        assertEquals(List.of("holder:c1", "holder:c2", "holder:c3"), oli.holderIds());

        Government dem = reloaded.get("dem-land").orElseThrow().government();
        assertEquals(GovernmentForm.DEMOCRACY, dem.form());
        assertEquals(3, dem.seatCount());
        assertEquals(SeatRole.REPRESENTATIVE, dem.seats().get(0).role());
        assertEquals("holder:r1", dem.seats().get(0).holderId().orElseThrow());
        assertEquals(99L, dem.seats().get(0).termEndsAtEpochMs().orElseThrow());

        // Spatial resolve after reload
        LookupResult r = reloaded.resolve("world", 150, 150);
        assertEquals("oli-land", r.territoryId().orElseThrow());
        assertEquals(GovernmentForm.OLIGARCHY, r.governmentForm().orElseThrow());
        assertEquals(original.get("mon-land").orElseThrow().government(), mon);
        assertEquals(original.get("oli-land").orElseThrow().government(), oli);
        assertEquals(original.get("dem-land").orElseThrow().government(), dem);
    }

    @Test
    void putGovernment_onRegistry() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("t", "T", "world", square(0, 50)));
        assertEquals(GovernmentForm.ANARCHY, registry.get("t").orElseThrow().governmentForm());
        registry.putGovernment("t", Government.democracy(List.of("rep:1")));
        assertEquals(GovernmentForm.DEMOCRACY, registry.get("t").orElseThrow().governmentForm());
        assertTrue(registry.get("t").orElseThrow().government().holderIds().contains("rep:1"));
    }
}
