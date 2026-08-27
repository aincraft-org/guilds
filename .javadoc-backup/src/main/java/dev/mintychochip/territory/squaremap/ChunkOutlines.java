package dev.mintychochip.territory.squaremap;

import dev.mintychochip.territory.model.ChunkPos;

import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.marker.MultiPolygon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges a chunk set into chunk-border-aligned outline polygons so the map
 * renders one clean boundary instead of one square per chunk.
 *
 * <p>Directed boundary edges (filled chunk on the right) are traced into
 * rings; rings are simplified by dropping collinear vertices, then classified
 * outer/hole by signed area. Holes attach to the outer ring containing them.
 * All output coordinates are block coordinates on chunk borders (multiples of
 * 16).</p>
 */
final class ChunkOutlines {

    /** Direction indices: 0=E, 1=S, 2=W, 3=N (x right, z down). */
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private ChunkOutlines() {
    }

    /** A traced, simplified outline ring in block coordinates. */
    record Ring(List<Point> vertices, boolean hole) {
    }

    /**
     * Traces every outline ring of the chunk set. Outers wind clockwise and
     * holes counter-clockwise in map coordinates (x right, z down).
     */
    static List<Ring> trace(Collection<ChunkPos> chunks) {
        Set<Long> filled = new HashSet<>();
        for (ChunkPos chunk : chunks) {
            filled.add(cellKey(chunk.chunkX(), chunk.chunkZ()));
        }
        if (filled.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Integer>> outgoing = boundaryEdges(filled);
        List<Ring> rings = new ArrayList<>();
        while (!outgoing.isEmpty()) {
            Map.Entry<Long, List<Integer>> seed = outgoing.entrySet().iterator().next();
            rings.add(traceRing(outgoing, seed.getKey()));
        }
        return rings;
    }

    /**
     * Groups traced rings into squaremap polygon parts: each outer ring plus
     * the holes it contains.
     */
    static List<MultiPolygon.MultiPolygonPart> toParts(Collection<ChunkPos> chunks) {
        List<Ring> rings = trace(chunks);
        List<Ring> outers = new ArrayList<>();
        List<Ring> holes = new ArrayList<>();
        for (Ring ring : rings) {
            if (ring.hole()) {
                holes.add(ring);
            } else {
                outers.add(ring);
            }
        }
        List<MultiPolygon.MultiPolygonPart> parts = new ArrayList<>();
        for (Ring outer : outers) {
            List<List<Point>> assigned = new ArrayList<>();
            for (Ring hole : holes) {
                if (contains(outer.vertices(), hole.vertices().get(0))) {
                    assigned.add(hole.vertices());
                }
            }
            parts.add(MultiPolygon.part(outer.vertices(), assigned));
        }
        return parts;
    }

    private static Map<Long, List<Integer>> boundaryEdges(Set<Long> filled) {
        Map<Long, List<Integer>> outgoing = new HashMap<>();
        for (long cell : filled) {
            int x = (int) (cell >> 32);
            int z = (int) cell;
            if (!filled.contains(cellKey(x, z - 1))) {
                addEdge(outgoing, x, z, 0);
            }
            if (!filled.contains(cellKey(x + 1, z))) {
                addEdge(outgoing, x + 1, z, 1);
            }
            if (!filled.contains(cellKey(x, z + 1))) {
                addEdge(outgoing, x + 1, z + 1, 2);
            }
            if (!filled.contains(cellKey(x - 1, z))) {
                addEdge(outgoing, x, z + 1, 3);
            }
        }
        return outgoing;
    }

    private static Ring traceRing(Map<Long, List<Integer>> outgoing, long startCorner) {
        List<long[]> corners = new ArrayList<>();
        corners.add(decode(startCorner));
        long corner = startCorner;
        int dir = takeEdge(outgoing, corner, -1);
        while (true) {
            corner = advance(corner, dir);
            if (corner == startCorner) {
                break;
            }
            corners.add(decode(corner));
            dir = takeEdge(outgoing, corner, dir);
        }
        List<long[]> simplified = simplify(corners);
        List<Point> vertices = new ArrayList<>(simplified.size());
        for (long[] v : simplified) {
            vertices.add(Point.of(v[0] * 16.0, v[1] * 16.0));
        }
        return new Ring(List.copyOf(vertices), signedArea2(simplified) < 0);
    }

    /** Takes the outgoing edge at a corner, preferring the sharpest right turn. */
    private static int takeEdge(Map<Long, List<Integer>> outgoing, long corner, int prevDir) {
        List<Integer> dirs = outgoing.get(corner);
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        for (Integer candidate : dirs) {
            int rank = prevDir < 0 ? 0 : turnRank(candidate, prevDir);
            if (rank < bestRank) {
                bestRank = rank;
                best = candidate;
            }
        }
        dirs.remove(Integer.valueOf(best));
        if (dirs.isEmpty()) {
            outgoing.remove(corner);
        }
        return best;
    }

    /** Right turn first, then straight, left, back. */
    private static int turnRank(int dir, int prevDir) {
        int turn = (dir - prevDir + 4) % 4;
        return switch (turn) {
            case 1 -> 0;
            case 0 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    private static List<long[]> simplify(List<long[]> corners) {
        int n = corners.size();
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long[] prev = corners.get((i - 1 + n) % n);
            long[] cur = corners.get(i);
            long[] next = corners.get((i + 1) % n);
            if (Long.signum(cur[0] - prev[0]) != Long.signum(next[0] - cur[0])
                    || Long.signum(cur[1] - prev[1]) != Long.signum(next[1] - cur[1])) {
                out.add(cur);
            }
        }
        return out;
    }

    /** Shoelace ×2; positive = clockwise (outer) in x-right/z-down coordinates. */
    private static long signedArea2(List<long[]> vertices) {
        long area = 0;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            long[] a = vertices.get(i);
            long[] b = vertices.get((i + 1) % n);
            area += a[0] * b[1] - b[0] * a[1];
        }
        return area;
    }

    private static boolean contains(List<Point> polygon, Point p) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = polygon.get(i);
            Point b = polygon.get(j);
            if ((a.z() > p.z()) != (b.z() > p.z())
                    && p.x() < (b.x() - a.x()) * (p.z() - a.z()) / (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static long advance(long corner, int dir) {
        long[] xz = decode(corner);
        return cornerKey((int) (xz[0] + DIRS[dir][0]), (int) (xz[1] + DIRS[dir][1]));
    }

    private static void addEdge(Map<Long, List<Integer>> outgoing, int x, int z, int dir) {
        outgoing.computeIfAbsent(cornerKey(x, z), k -> new ArrayList<>()).add(dir);
    }

    private static long cellKey(int x, int z) {
        return cornerKey(x, z);
    }

    private static long cornerKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long[] decode(long key) {
        return new long[]{(int) (key >> 32), (int) key};
    }
}
