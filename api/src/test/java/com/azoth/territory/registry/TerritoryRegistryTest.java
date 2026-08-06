package com.azoth.territory.registry;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.ChunkPos;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.Zone;
import com.azoth.territory.model.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link TerritoryRegistry#resolve} path with representative geometries.
 */
class TerritoryRegistryTest {

    private TerritoryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TerritoryRegistry();
    }

    @Test
    void polygonTerritory_insideAndOutside() {
        Territory t = new Territory(
                "everfall",
                "Everfall",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(500, 0),
                        new BlockPos(500, 500),
                        new BlockPos(0, 500)
                ))
        );
        registry.register(t);

        LookupResult inside = registry.resolve("world", 250, 250);
        assertTrue(inside.isContained());
        assertEquals("everfall", inside.territoryId().orElseThrow());
        assertEquals(ZoneType.WILDERNESS, inside.zoneType().orElseThrow());
        assertTrue(inside.zone().orElseThrow().isDefault());

        LookupResult outside = registry.resolve("world", 600, 250);
        assertFalse(outside.isContained());
        assertTrue(outside.territoryId().isEmpty());
        assertTrue(outside.zoneType().isEmpty());
    }

    @Test
    void chunkBoundaryTerritory_resolvesByChunkMembership() {
        // Chunks (10,20) and (10,21)
        Territory t = new Territory(
                "brightwood",
                "Brightwood",
                "world",
                Boundary.ofChunks(Set.of(
                        new ChunkPos(10, 20),
                        new ChunkPos(10, 21)
                ))
        );
        registry.register(t);

        // Block in chunk (10,20): 10*16=160 .. 175
        LookupResult hit = registry.resolve("world", 160, 320);
        assertTrue(hit.isContained());
        assertEquals("brightwood", hit.territoryId().orElseThrow());

        LookupResult miss = registry.resolve("world", 200, 320);
        assertFalse(miss.isContained());
    }

    @Test
    void wildernessAndClaimableZones_resolveCorrectTypes() {
        Boundary outer = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0),
                new BlockPos(1000, 0),
                new BlockPos(1000, 1000),
                new BlockPos(0, 1000)
        ));
        Zone wild = new Zone(
                "wild-north",
                "North Woods",
                ZoneType.WILDERNESS,
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 500),
                        new BlockPos(500, 500),
                        new BlockPos(500, 1000),
                        new BlockPos(0, 1000)
                )),
                1
        );
        Zone claimable = new Zone(
                "town-plot",
                "Town Plot A",
                ZoneType.CLAIMABLE,
                Boundary.ofPolygon(List.of(
                        new BlockPos(100, 100),
                        new BlockPos(200, 100),
                        new BlockPos(200, 200),
                        new BlockPos(100, 200)
                )),
                10
        );
        Territory t = new Territory(
                "windsward",
                "Windsward",
                "world",
                outer,
                List.of(wild, claimable),
                ZoneType.WILDERNESS
        );
        registry.register(t);

        // Inside claimable plot
        LookupResult claim = registry.resolve("world", 150, 150);
        assertTrue(claim.isContained());
        assertEquals("windsward", claim.territoryId().orElseThrow());
        assertEquals(ZoneType.CLAIMABLE, claim.zoneType().orElseThrow());
        assertEquals("town-plot", claim.zone().orElseThrow().zoneId());
        assertFalse(claim.zone().orElseThrow().isDefault());

        // Inside named wilderness zone
        LookupResult wildHit = registry.resolve("world", 250, 750);
        assertEquals(ZoneType.WILDERNESS, wildHit.zoneType().orElseThrow());
        assertEquals("wild-north", wildHit.zone().orElseThrow().zoneId());

        // Inside territory but outside named zones → default Wilderness
        LookupResult def = registry.resolve("world", 800, 200);
        assertEquals(ZoneType.WILDERNESS, def.zoneType().orElseThrow());
        assertTrue(def.zone().orElseThrow().isDefault());
    }

    @Test
    void outsideAllTerritories_uncontained() {
        registry.register(new Territory(
                "a", "A", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10)
                ))
        ));
        LookupResult r = registry.resolve("world", 9999, 9999);
        assertFalse(r.isContained());
        assertEquals("LookupResult{uncontained}", r.toString());
    }

    @Test
    void wrongWorld_uncontainedEvenIfCoordsMatch() {
        registry.register(new Territory(
                "a", "A", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)
                ))
        ));
        assertFalse(registry.resolve("nether", 50, 50).isContained());
        assertTrue(registry.resolve("world", 50, 50).isContained());
    }

    @Test
    void nonOverlappingZones_resolveIndependently() {
        Boundary outer = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        Zone a = new Zone(
                "a", "A", ZoneType.WILDERNESS,
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(40, 0),
                        new BlockPos(40, 40), new BlockPos(0, 40)
                )),
                1
        );
        Zone b = new Zone(
                "b", "B", ZoneType.CLAIMABLE,
                Boundary.ofPolygon(List.of(
                        new BlockPos(40, 0), new BlockPos(80, 0),
                        new BlockPos(80, 40), new BlockPos(40, 40)
                )),
                5
        );
        registry.register(new Territory("t", "T", "world", outer, List.of(a, b), ZoneType.WILDERNESS));

        assertEquals(ZoneType.WILDERNESS, registry.resolve("world", 20, 20).zoneType().orElseThrow());
        assertEquals("a", registry.resolve("world", 20, 20).zone().orElseThrow().zoneId());
        assertEquals(ZoneType.CLAIMABLE, registry.resolve("world", 60, 20).zoneType().orElseThrow());
        assertEquals("b", registry.resolve("world", 60, 20).zone().orElseThrow().zoneId());
    }

    @Test
    void overlappingTerritories_rejected() {
        registry.register(new Territory(
                "a", "A", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)
                ))
        ));
        Territory clash = new Territory(
                "b", "B", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(50, 50), new BlockPos(150, 50),
                        new BlockPos(150, 150), new BlockPos(50, 150)
                ))
        );
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(clash)
        );
        assertTrue(ex.getMessage().contains("must not overlap"));
        assertEquals(1, registry.size());
    }

    @Test
    void adjacentTerritories_allowed() {
        registry.register(new Territory(
                "west", "West", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100)
                ))
        ));
        registry.register(new Territory(
                "east", "East", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(100, 0), new BlockPos(200, 0),
                        new BlockPos(200, 100), new BlockPos(100, 100)
                ))
        ));
        assertEquals(2, registry.size());
        assertEquals("west", registry.resolve("world", 50, 50).territoryId().orElseThrow());
        assertEquals("east", registry.resolve("world", 150, 50).territoryId().orElseThrow());
    }

    @Test
    void sameIdReplace_allowedEvenIfGeometryUnchanged() {
        Territory t = new Territory(
                "solo", "Solo", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(50, 0),
                        new BlockPos(50, 50), new BlockPos(0, 50)
                ))
        );
        registry.register(t);
        registry.register(t); // replace self
        assertEquals(1, registry.size());
    }

    @Test
    void differentWorlds_mayUseSameGeometry() {
        Boundary square = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        registry.register(new Territory("overworld", "OW", "world", square));
        registry.register(new Territory("nether-copy", "N", "world_nether", square));
        assertEquals(2, registry.size());
    }

    @Test
    void registerListAndUnregister() {
        Territory t = new Territory(
                "x", "X", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(0, 0)))
        );
        registry.register(t);
        assertEquals(1, registry.size());
        assertEquals(1, registry.list().size());
        assertTrue(registry.get("x").isPresent());
        assertTrue(registry.unregister("x"));
        assertEquals(0, registry.size());
    }

    @Test
    void resolveViaBlockPos() {
        registry.register(new Territory(
                "p", "P", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(50, 0),
                        new BlockPos(50, 50), new BlockPos(0, 50)
                ))
        ));
        LookupResult r = registry.resolve("world", new BlockPos(25, 25));
        assertEquals("p", r.territoryId().orElseThrow());
    }
}
