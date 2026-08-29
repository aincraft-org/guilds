package org.aincraft.guilds.territory.worldguard;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.ChunkPos;
import org.aincraft.guilds.territory.model.Territory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRegionPlannerTest {

    @Test
    void chunkBoundary_producesOneCuboidSpecPerChunk() {
        Territory territory = new Territory("frostfen", "Frostfen", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1))));

        List<TerritoryRegionPlanner.RegionSpec> specs = TerritoryRegionPlanner.plan(territory, Set.of());

        assertEquals(3, specs.size());
        for (TerritoryRegionPlanner.RegionSpec spec : specs) {
            assertTrue(spec.isCuboid());
            assertTrue(spec.id().startsWith("guilds-frostfen-"));
        }
        assertEquals(Set.of(
                "guilds-frostfen-0_0",
                "guilds-frostfen-1_0",
                "guilds-frostfen-0_1"
        ), specs.stream().map(TerritoryRegionPlanner.RegionSpec::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void polygonBoundary_producesOneMergedPolygonSpec() {
        Territory territory = new Territory("thornwood", "Thornwood", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))));

        List<TerritoryRegionPlanner.RegionSpec> specs = TerritoryRegionPlanner.plan(territory, Set.of());

        assertEquals(1, specs.size());
        TerritoryRegionPlanner.RegionSpec spec = specs.get(0);
        assertEquals("guilds-thornwood", spec.id());
        assertTrue(!spec.isCuboid());
        assertEquals(4, spec.polygon().size());
    }

    @Test
    void owners_areCarriedIntoEverySpecForTheTerritory() {
        Territory territory = new Territory("keep", "Keep", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(5, 5))));
        UUID member = UUID.randomUUID();

        List<TerritoryRegionPlanner.RegionSpec> specs = TerritoryRegionPlanner.plan(territory, Set.of(member));

        assertEquals(1, specs.size());
        assertEquals(Set.of(member), specs.get(0).owners());
    }

    @Test
    void signature_changesWhenOwnersChange() {
        Territory territory = new Territory("keep", "Keep", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(5, 5))));
        TerritoryRegionPlanner.RegionSpec noOwners = TerritoryRegionPlanner.plan(territory, Set.of()).get(0);
        TerritoryRegionPlanner.RegionSpec withOwner =
                TerritoryRegionPlanner.plan(territory, Set.of(UUID.randomUUID())).get(0);

        assertNotEquals(
                TerritoryRegionPlanner.signatureOf(noOwners),
                TerritoryRegionPlanner.signatureOf(withOwner)
        );
    }

    @Test
    void signature_isStableRegardlessOfOwnerIterationOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        TerritoryRegionPlanner.RegionSpec spec1 = new TerritoryRegionPlanner.RegionSpec(
                "id", new ChunkPos(5, 5), null, new java.util.LinkedHashSet<>(List.of(a, b)));
        TerritoryRegionPlanner.RegionSpec spec2 = new TerritoryRegionPlanner.RegionSpec(
                "id", new ChunkPos(5, 5), null, new java.util.LinkedHashSet<>(List.of(b, a)));

        assertEquals(TerritoryRegionPlanner.signatureOf(spec1), TerritoryRegionPlanner.signatureOf(spec2));
    }

    @Test
    void keyPart_sanitizesDisallowedCharactersAndFoldsCase() {
        assertEquals("frostfen_s_reach", TerritoryRegionPlanner.keyPart("Frostfen's Reach"));
        assertEquals("unnamed", TerritoryRegionPlanner.keyPart(""));
    }
}
