package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlapRulesTest {

    @Test
    void polygons_interiorOverlap_detected() {
        Boundary a = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        Boundary b = Boundary.ofPolygon(List.of(
                new BlockPos(50, 50), new BlockPos(150, 50),
                new BlockPos(150, 150), new BlockPos(50, 150)
        ));
        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
    }

    @Test
    void polygons_edgeAdjacent_notOverlap() {
        Boundary west = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        Boundary east = Boundary.ofPolygon(List.of(
                new BlockPos(100, 0), new BlockPos(200, 0),
                new BlockPos(200, 100), new BlockPos(100, 100)
        ));
        assertFalse(west.overlaps(east));
        assertFalse(east.overlaps(west));
    }

    @Test
    void identicalPolygons_overlap() {
        Boundary a = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(50, 0),
                new BlockPos(50, 50), new BlockPos(0, 50)
        ));
        Boundary b = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(50, 0),
                new BlockPos(50, 50), new BlockPos(0, 50)
        ));
        assertTrue(a.overlaps(b));
    }

    @Test
    void nestedPolygon_overlaps() {
        Boundary outer = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        Boundary inner = Boundary.ofPolygon(List.of(
                new BlockPos(20, 20), new BlockPos(40, 20),
                new BlockPos(40, 40), new BlockPos(20, 40)
        ));
        assertTrue(outer.overlaps(inner));
    }

    @Test
    void sameChunk_overlaps_adjacentChunkDoesNot() {
        Boundary a = Boundary.ofChunks(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0)));
        Boundary b = Boundary.ofChunks(Set.of(new ChunkPos(1, 0), new ChunkPos(2, 0)));
        Boundary c = Boundary.ofChunks(Set.of(new ChunkPos(3, 0)));
        assertTrue(a.overlaps(b));
        assertFalse(a.overlaps(c));
    }

    @Test
    void polygonAndChunk_overlapWhenChunkInside() {
        Boundary poly = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        ));
        // chunk (2,2) → blocks 32..47
        Boundary chunk = Boundary.ofChunks(Set.of(new ChunkPos(2, 2)));
        Boundary far = Boundary.ofChunks(Set.of(new ChunkPos(50, 50)));
        assertTrue(poly.overlaps(chunk));
        assertFalse(poly.overlaps(far));
    }

    @Test
    void territory_rejectsOverlappingZones() {
        Boundary outer = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(200, 0),
                new BlockPos(200, 200), new BlockPos(0, 200)
        ));
        Zone z1 = new Zone("z1", "Z1", ZoneType.WILDERNESS, Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        )));
        Zone z2 = new Zone("z2", "Z2", ZoneType.CLAIMABLE, Boundary.ofPolygon(List.of(
                new BlockPos(50, 50), new BlockPos(150, 50),
                new BlockPos(150, 150), new BlockPos(50, 150)
        )));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Territory("t", "T", "world", outer, List.of(z1, z2), ZoneType.WILDERNESS)
        );
        assertTrue(ex.getMessage().contains("zones must not overlap"));
    }

    @Test
    void territory_allowsAdjacentZones() {
        Boundary outer = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(200, 0),
                new BlockPos(200, 200), new BlockPos(0, 200)
        ));
        Zone z1 = new Zone("z1", "Z1", ZoneType.WILDERNESS, Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)
        )));
        Zone z2 = new Zone("z2", "Z2", ZoneType.CLAIMABLE, Boundary.ofPolygon(List.of(
                new BlockPos(100, 0), new BlockPos(200, 0),
                new BlockPos(200, 100), new BlockPos(100, 100)
        )));
        Territory t = new Territory("t", "T", "world", outer, List.of(z1, z2), ZoneType.WILDERNESS);
        assertTrue(t.zone("z1").isPresent());
        assertTrue(t.zone("z2").isPresent());
    }
}
