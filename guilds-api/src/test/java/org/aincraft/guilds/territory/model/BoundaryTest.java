package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryTest {

    @Test
    void polygon_containsInteriorAndExcludesExterior() {
        // Axis-aligned square: (0,0)-(100,0)-(100,100)-(0,100)
        Boundary b = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0),
                new BlockPos(100, 0),
                new BlockPos(100, 100),
                new BlockPos(0, 100)
        ));

        assertTrue(b.contains(50, 50), "center should be inside");
        assertTrue(b.contains(1, 1), "near corner inside");
        assertTrue(b.contains(0, 0), "vertex counts as inside");
        assertTrue(b.contains(50, 0), "edge counts as inside");
        assertFalse(b.contains(150, 50), "east of square");
        assertFalse(b.contains(-1, 50), "west of square");
        assertFalse(b.contains(50, 150), "north of square");
        assertFalse(b.contains(50, -1), "south of square");
    }

    @Test
    void polygon_multiVertexIrregularContainsCorrectly() {
        // Triangle-ish: (0,0)-(200,0)-(100,150)
        Boundary b = Boundary.ofPolygon(List.of(
                new BlockPos(0, 0),
                new BlockPos(200, 0),
                new BlockPos(100, 150)
        ));
        assertTrue(b.contains(100, 50));
        assertFalse(b.contains(10, 140));
        assertFalse(b.contains(300, 0));
    }

    @Test
    void chunks_containBlocksInListedChunksOnly() {
        // Chunk (0,0) = blocks 0..15 x 0..15; chunk (1,0) = 16..31 x 0..15
        Boundary b = Boundary.ofChunks(Set.of(
                new ChunkPos(0, 0),
                new ChunkPos(1, 0)
        ));
        assertTrue(b.contains(0, 0));
        assertTrue(b.contains(15, 15));
        assertTrue(b.contains(16, 8));
        assertTrue(b.contains(31, 0));
        assertFalse(b.contains(32, 0));
        assertFalse(b.contains(8, 16));
        assertFalse(b.contains(-1, 0));
    }

    @Test
    void combined_polygonOrChunksIsUnion() {
        Boundary b = Boundary.of(
                List.of(
                        new BlockPos(0, 0),
                        new BlockPos(10, 0),
                        new BlockPos(10, 10),
                        new BlockPos(0, 10)
                ),
                Set.of(new ChunkPos(5, 5)) // blocks 80..95
        );
        assertTrue(b.contains(5, 5), "inside polygon");
        assertTrue(b.contains(88, 88), "inside listed chunk");
        assertFalse(b.contains(50, 50), "neither");
    }

    @Test
    void emptyBoundaryContainsNothing() {
        assertFalse(Boundary.empty().contains(0, 0));
    }

    @Test
    void polygonRequiresThreeVertices() {
        assertThrows(IllegalArgumentException.class,
                () -> Boundary.ofPolygon(List.of(new BlockPos(0, 0), new BlockPos(1, 1))));
    }
}
