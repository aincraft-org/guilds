package com.azoth.territory.squaremap;

import com.azoth.territory.model.ChunkPos;

import org.junit.jupiter.api.Test;

import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.marker.MultiPolygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkOutlinesTest {

    @Test
    void singleChunk_tracesChunkRectangle() {
        List<ChunkOutlines.Ring> rings = ChunkOutlines.trace(List.of(new ChunkPos(0, 0)));
        assertEquals(1, rings.size());
        ChunkOutlines.Ring ring = rings.get(0);
        assertFalse(ring.hole());
        assertEquals(
                rotateFirst(List.of(
                        Point.of(0, 0),
                        Point.of(16, 0),
                        Point.of(16, 16),
                        Point.of(0, 16)
                )),
                rotateFirst(ring.vertices())
        );
    }

    @Test
    void solidBlock_mergesIntoOneRectangleWithNoCollinearVertices() {
        // 16x12 chunk block, matching the Frostfen seed.
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = -200; cx < -184; cx++) {
            for (int cz = 60; cz < 72; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        List<ChunkOutlines.Ring> rings = ChunkOutlines.trace(chunks);
        assertEquals(1, rings.size());
        ChunkOutlines.Ring ring = rings.get(0);
        assertFalse(ring.hole());
        assertEquals(
                rotateFirst(List.of(
                        Point.of(-3200, 960),
                        Point.of(-2944, 960),
                        Point.of(-2944, 1152),
                        Point.of(-3200, 1152)
                )),
                rotateFirst(ring.vertices())
        );
    }

    @Test
    void ringOfChunks_yieldsOuterAndHole() {
        // 3x3 chunk square with the center missing → outer ring + hole.
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                if (cx == 1 && cz == 1) {
                    continue;
                }
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        List<ChunkOutlines.Ring> rings = ChunkOutlines.trace(chunks);
        assertEquals(2, rings.size());
        List<ChunkOutlines.Ring> outers = rings.stream().filter(r -> !r.hole()).toList();
        List<ChunkOutlines.Ring> holes = rings.stream().filter(ChunkOutlines.Ring::hole).toList();
        assertEquals(1, outers.size());
        assertEquals(1, holes.size());
        assertEquals(
                rotateFirst(List.of(
                        Point.of(0, 0), Point.of(48, 0), Point.of(48, 48), Point.of(0, 48)
                )),
                rotateFirst(outers.get(0).vertices())
        );
        assertEquals(
                rotateFirst(List.of(
                        Point.of(16, 16), Point.of(16, 32), Point.of(32, 32), Point.of(32, 16)
                )),
                rotateFirst(holes.get(0).vertices())
        );
    }

    @Test
    void lShape_collinearVerticesCollapsed() {
        // L: (0,0),(1,0),(0,1) in chunk coordinates → 6-vertex outline.
        List<ChunkPos> chunks = List.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1));
        List<ChunkOutlines.Ring> rings = ChunkOutlines.trace(chunks);
        assertEquals(1, rings.size());
        assertEquals(
                rotateFirst(List.of(
                        Point.of(0, 0), Point.of(32, 0), Point.of(32, 16),
                        Point.of(16, 16), Point.of(16, 32), Point.of(0, 32)
                )),
                rotateFirst(rings.get(0).vertices())
        );
    }

    @Test
    void disjointIslands_traceSeparateOuters() {
        List<ChunkPos> chunks = List.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1),
                new ChunkPos(10, 10), new ChunkPos(11, 10), new ChunkPos(11, 11)
        );
        List<ChunkOutlines.Ring> rings = ChunkOutlines.trace(chunks);
        assertEquals(2, rings.size());
        assertTrue(rings.stream().noneMatch(ChunkOutlines.Ring::hole));
    }

    @Test
    void toParts_attachesHoleToContainingOuter() {
        // One hollow 3x3 ring (outer + hole = 1 part) plus two single chunks.
        List<ChunkPos> chunks = new ArrayList<>(IntStream.rangeClosed(0, 2)
                .boxed().flatMap(cx -> IntStream.rangeClosed(0, 2).boxed().map(cz -> new ChunkPos(cx, cz)))
                .toList());
        chunks.removeIf(c -> c.chunkX() == 1 && c.chunkZ() == 1);
        chunks.add(new ChunkPos(10, 0));
        chunks.add(new ChunkPos(20, 0));

        List<MultiPolygon.MultiPolygonPart> parts = ChunkOutlines.toParts(chunks);
        assertEquals(3, parts.size());
        long withHoles = parts.stream().filter(p -> !p.negativeSpace().isEmpty()).count();
        assertEquals(1, withHoles);
        Set<Point> holePoints = parts.stream()
                .filter(p -> !p.negativeSpace().isEmpty())
                .flatMap(p -> p.negativeSpace().get(0).stream())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                Point.of(16, 16), Point.of(16, 32), Point.of(32, 32), Point.of(32, 16)
        ), holePoints);
    }

    /** Rotates a ring so its lexicographically smallest vertex comes first. */
    private static List<Point> rotateFirst(List<Point> vertices) {
        Point min = vertices.get(0);
        for (Point v : vertices) {
            if (v.x() < min.x() || (v.x() == min.x() && v.z() < min.z())) {
                min = v;
            }
        }
        int idx = vertices.indexOf(min);
        List<Point> out = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            out.add(vertices.get((idx + i) % vertices.size()));
        }
        return List.copyOf(out);
    }
}
