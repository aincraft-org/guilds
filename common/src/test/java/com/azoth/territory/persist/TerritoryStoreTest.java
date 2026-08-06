package com.azoth.territory.persist;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.ChunkPos;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.Zone;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real register → resolve → save → load → resolve again on shipped store + registry.
 */
class TerritoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLoad_preservesResolveResults() throws Exception {
        TerritoryRegistry original = new TerritoryRegistry();

        Zone claimable = new Zone(
                "plot-1",
                "Plot 1",
                ZoneType.CLAIMABLE,
                Boundary.ofPolygon(List.of(
                        new BlockPos(100, 100),
                        new BlockPos(200, 100),
                        new BlockPos(200, 200),
                        new BlockPos(100, 200)
                )),
                10
        );
        Zone wild = new Zone(
                "wild-edge",
                "Wild Edge",
                ZoneType.WILDERNESS,
                Boundary.ofChunks(Set.of(new ChunkPos(30, 30))),
                5
        );
        Territory poly = new Territory(
                "everfall",
                "Everfall",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(1000, 0),
                        new BlockPos(1000, 1000),
                        new BlockPos(0, 1000)
                )),
                List.of(claimable, wild),
                ZoneType.WILDERNESS
        );
        Territory chunksOnly = new Territory(
                "outpost",
                "Outpost",
                "world",
                Boundary.ofChunks(Set.of(
                        new ChunkPos(100, 100),
                        new ChunkPos(100, 101)
                ))
        );
        original.register(poly);
        original.register(chunksOnly);

        // Capture resolve results before save
        LookupResult claimBefore = original.resolve("world", 150, 150);
        LookupResult wildDefaultBefore = original.resolve("world", 500, 500);
        LookupResult wildZoneBefore = original.resolve("world", 30 * 16 + 4, 30 * 16 + 4);
        LookupResult outpostBefore = original.resolve("world", 100 * 16 + 1, 100 * 16 + 1);
        LookupResult outsideBefore = original.resolve("world", 5000, 5000);

        assertEquals(ZoneType.CLAIMABLE, claimBefore.zoneType().orElseThrow());
        assertEquals("everfall", claimBefore.territoryId().orElseThrow());
        assertEquals(ZoneType.WILDERNESS, wildDefaultBefore.zoneType().orElseThrow());
        assertTrue(wildDefaultBefore.zone().orElseThrow().isDefault());
        assertEquals(ZoneType.WILDERNESS, wildZoneBefore.zoneType().orElseThrow());
        assertEquals("wild-edge", wildZoneBefore.zone().orElseThrow().zoneId());
        assertEquals("outpost", outpostBefore.territoryId().orElseThrow());
        assertFalse(outsideBefore.isContained());

        Path file = tempDir.resolve("territories.json");
        TerritoryStore store = new TerritoryStore(file);
        store.save(original);
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.size(file) > 50);

        // Fresh registry + load
        TerritoryRegistry reloaded = new TerritoryRegistry();
        store.loadInto(reloaded);
        assertEquals(2, reloaded.size());

        LookupResult claimAfter = reloaded.resolve("world", 150, 150);
        LookupResult wildDefaultAfter = reloaded.resolve("world", 500, 500);
        LookupResult wildZoneAfter = reloaded.resolve("world", 30 * 16 + 4, 30 * 16 + 4);
        LookupResult outpostAfter = reloaded.resolve("world", 100 * 16 + 1, 100 * 16 + 1);
        LookupResult outsideAfter = reloaded.resolve("world", 5000, 5000);

        assertEquals(claimBefore.territoryId(), claimAfter.territoryId());
        assertEquals(claimBefore.zoneType(), claimAfter.zoneType());
        assertEquals(claimBefore.zone().orElseThrow().zoneId(), claimAfter.zone().orElseThrow().zoneId());

        assertEquals(wildDefaultBefore.zoneType(), wildDefaultAfter.zoneType());
        assertEquals(wildDefaultBefore.zone().orElseThrow().isDefault(), wildDefaultAfter.zone().orElseThrow().isDefault());

        assertEquals(wildZoneBefore.zone().orElseThrow().zoneId(), wildZoneAfter.zone().orElseThrow().zoneId());
        assertEquals(outpostBefore.territoryId(), outpostAfter.territoryId());
        assertEquals(outsideBefore.isContained(), outsideAfter.isContained());

        // Structural equality of reloaded territories
        assertEquals(original.get("everfall").orElseThrow(), reloaded.get("everfall").orElseThrow());
        assertEquals(original.get("outpost").orElseThrow(), reloaded.get("outpost").orElseThrow());
    }

    @Test
    void loadMissingFile_clearsRegistry() throws Exception {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(new Territory(
                "tmp", "Tmp", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(0, 0)))
        ));
        TerritoryStore store = new TerritoryStore(tempDir.resolve("does-not-exist.json"));
        store.loadInto(reg);
        assertEquals(0, reg.size());
    }
}
