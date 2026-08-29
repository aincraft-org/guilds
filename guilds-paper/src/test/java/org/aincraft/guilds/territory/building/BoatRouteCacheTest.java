package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.building.boat.BoatRouteCache;
import org.aincraft.guilds.territory.building.boat.BoatRouteResult;
import org.aincraft.guilds.territory.building.boat.BoatWaterMask;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatRouteCacheTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void reusesGeometryAndNormalizesEndpointOrder() {
        BoatRouteCache cache = new BoatRouteCache();
        BoatWaterMask.Cell first = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell second = new BoatWaterMask.Cell(2, 62, 0);
        BoatRouteResult result = BoatRouteResult.connected(2.0);

        cache.put(WORLD, first, second, result);

        assertEquals(Optional.of(result), cache.get(WORLD, second, first));
        assertEquals(0L, cache.currentRevision(WORLD));
    }

    @Test
    void invalidatesChangedAndNeighboringChunks() {
        BoatRouteCache cache = new BoatRouteCache();
        BoatWaterMask.Cell first = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell second = new BoatWaterMask.Cell(16, 62, 0);
        BoatRouteResult result = BoatRouteResult.connected(16.0);

        cache.put(WORLD, first, second, result);
        long revision = cache.invalidateChunk(WORLD, 1, 1);

        assertEquals(1L, revision);
        assertTrue(cache.get(WORLD, first, second).isEmpty());
    }

    @Test
    void rejectsStaleRevisionWrites() {
        BoatRouteCache cache = new BoatRouteCache();
        BoatWaterMask.Cell first = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell second = new BoatWaterMask.Cell(1, 62, 0);
        BoatRouteCache.Key staleKey = cache.key(WORLD, first, second);
        cache.invalidateChunk(WORLD, 0, 0);

        assertFalse(cache.put(staleKey, BoatRouteResult.connected(1.0)));
        assertTrue(cache.get(WORLD, first, second).isEmpty());
    }
}
