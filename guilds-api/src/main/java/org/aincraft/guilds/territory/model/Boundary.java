package org.aincraft.guilds.territory.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Spatial extent of a territory or zone.
 * <p>
 * Supports polygonal vertices (block XZ) and/or an explicit set of chunks.
 * A location is inside if it matches <em>either</em> representation when that
 * representation is non-empty (union). If both are empty, nothing is contained.
 */
public record Boundary(List<BlockPos> polygon, Set<ChunkPos> chunks) {

    public Boundary {
        polygon = polygon == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(polygon));
        chunks = chunks == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(chunks));
    }

    public static Boundary empty() {
        return new Boundary(List.of(), Set.of());
    }

    public static Boundary ofPolygon(Collection<BlockPos> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("polygon requires at least 3 vertices, got " + vertices.size());
        }
        return new Boundary(new ArrayList<>(vertices), Set.of());
    }

    public static Boundary ofChunks(Collection<ChunkPos> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunk boundary requires at least one chunk");
        }
        return new Boundary(List.of(), new LinkedHashSet<>(chunks));
    }

    public static Boundary of(Collection<BlockPos> vertices, Collection<ChunkPos> chunks) {
        List<BlockPos> poly = vertices == null ? List.of() : new ArrayList<>(vertices);
        Set<ChunkPos> ch = chunks == null ? Set.of() : new LinkedHashSet<>(chunks);
        if (poly.isEmpty() && ch.isEmpty()) {
            return empty();
        }
        if (!poly.isEmpty() && poly.size() < 3) {
            throw new IllegalArgumentException("polygon requires at least 3 vertices, got " + poly.size());
        }
        return new Boundary(poly, ch);
    }


    public boolean hasPolygon() {
        return polygon.size() >= 3;
    }

    public boolean hasChunks() {
        return !chunks.isEmpty();
    }

    public boolean isEmpty() {
        return !hasPolygon() && !hasChunks();
    }

    /**
     * True if the block position is inside the polygon (if defined) or in a
     * listed chunk (if defined). Empty boundary never contains.
     */
    public boolean contains(int blockX, int blockZ) {
        if (isEmpty()) {
            return false;
        }
        if (hasPolygon() && pointInPolygon(blockX, blockZ, polygon)) {
            return true;
        }
        if (hasChunks()) {
            ChunkPos c = ChunkPos.fromBlock(blockX, blockZ);
            return chunks.contains(c);
        }
        return false;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.x(), pos.z());
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        if (hasChunks() && chunks.contains(new ChunkPos(chunkX, chunkZ))) {
            return true;
        }
        // A chunk is "contained" by a pure polygon if its center lies inside.
        if (hasPolygon()) {
            int centerX = chunkX * 16 + 8;
            int centerZ = chunkZ * 16 + 8;
            return pointInPolygon(centerX, centerZ, polygon);
        }
        return false;
    }

    /**
     * True if this boundary and {@code other} share any <em>interior</em> area.
     * Touching only on edges/corners (adjacent territories/zones) is allowed and
     * returns {@code false}. Same chunk ids count as overlap.
     */
    public boolean overlaps(Boundary other) {
        Objects.requireNonNull(other, "other");
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        if (hasChunks() && other.hasChunks()) {
            for (ChunkPos c : chunks) {
                if (other.chunks.contains(c)) {
                    return true;
                }
            }
        }
        if (hasPolygon() && other.hasPolygon() && polygonsOverlap(polygon, other.polygon)) {
            return true;
        }
        if (hasPolygon() && other.hasChunks() && polygonOverlapsChunks(polygon, other.chunks)) {
            return true;
        }
        if (other.hasPolygon() && hasChunks() && polygonOverlapsChunks(other.polygon, chunks)) {
            return true;
        }
        return false;
    }

    /**
     * Ray-casting point-in-polygon (even-odd rule) on block XZ plane.
     * Vertices are treated as closed; collinear edge hits count as inside.
     */
    static boolean pointInPolygon(int x, int z, List<BlockPos> vertices) {
        if (vertices.size() < 3) {
            return false;
        }
        // On-vertex / on-edge: treat as inside for stable gameplay queries.
        if (onBoundary(x, z, vertices)) {
            return true;
        }
        return pointInPolygonStrict(x, z, vertices);
    }

    /**
     * Even-odd interior only — points on the boundary are not inside.
     * Used for overlap tests so adjacent regions that only share an edge are fine.
     */
    static boolean pointInPolygonStrict(int x, int z, List<BlockPos> vertices) {
        if (vertices.size() < 3) {
            return false;
        }
        if (onBoundary(x, z, vertices)) {
            return false;
        }
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = vertices.get(i).x();
            int zi = vertices.get(i).z();
            int xj = vertices.get(j).x();
            int zj = vertices.get(j).z();
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (double) (xj - xi) * (z - zi) / (double) (zj - zi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    static boolean polygonsOverlap(List<BlockPos> a, List<BlockPos> b) {
        if (a.size() < 3 || b.size() < 3) {
            return false;
        }
        // Vertex of one strictly inside the other.
        for (BlockPos p : a) {
            if (pointInPolygonStrict(p.x(), p.z(), b)) {
                return true;
            }
        }
        for (BlockPos p : b) {
            if (pointInPolygonStrict(p.x(), p.z(), a)) {
                return true;
            }
        }
        // Proper (non-endpoint-only) edge crossings.
        int na = a.size();
        int nb = b.size();
        for (int i = 0, ia = na - 1; i < na; ia = i++) {
            int ax = a.get(ia).x();
            int az = a.get(ia).z();
            int bx = a.get(i).x();
            int bz = a.get(i).z();
            for (int j = 0, jb = nb - 1; j < nb; jb = j++) {
                int cx = b.get(jb).x();
                int cz = b.get(jb).z();
                int dx = b.get(j).x();
                int dz = b.get(j).z();
                if (segmentsProperlyIntersect(ax, az, bx, bz, cx, cz, dx, dz)) {
                    return true;
                }
            }
        }
        // Interior sample via vertex average (works for convex territories / typical map polygons).
        if (interiorSampleOverlaps(a, b) || interiorSampleOverlaps(b, a)) {
            return true;
        }
        return false;
    }

    private static boolean interiorSampleOverlaps(List<BlockPos> sampleFrom, List<BlockPos> other) {
        double[] c = centroid(sampleFrom);
        int[][] samples = {
                {(int) Math.floor(c[0]), (int) Math.floor(c[1])},
                {(int) Math.round(c[0]), (int) Math.round(c[1])},
                {(int) Math.ceil(c[0]) - 1, (int) Math.ceil(c[1]) - 1}
        };
        for (int[] s : samples) {
            if (pointInPolygonStrict(s[0], s[1], sampleFrom)
                    && pointInPolygonStrict(s[0], s[1], other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean polygonOverlapsChunks(List<BlockPos> poly, Set<ChunkPos> chunkSet) {
        for (ChunkPos c : chunkSet) {
            List<BlockPos> rect = chunkAsPolygon(c);
            if (polygonsOverlap(poly, rect)) {
                return true;
            }
        }
        return false;
    }

    /** Exclusive-max rectangle for a chunk so adjacent chunks only share edges. */
    static List<BlockPos> chunkAsPolygon(ChunkPos c) {
        int minX = c.chunkX() * 16;
        int minZ = c.chunkZ() * 16;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        return List.of(
                new BlockPos(minX, minZ),
                new BlockPos(maxX, minZ),
                new BlockPos(maxX, maxZ),
                new BlockPos(minX, maxZ)
        );
    }

    private static double[] centroid(List<BlockPos> vertices) {
        double x = 0;
        double z = 0;
        for (BlockPos p : vertices) {
            x += p.x();
            z += p.z();
        }
        return new double[]{x / vertices.size(), z / vertices.size()};
    }

    /**
     * True if segments AB and CD cross at a point interior to both (not only an endpoint).
     * Collinear / touching-at-corner cases return false (adjacency allowed).
     */
    static boolean segmentsProperlyIntersect(
            int ax, int az, int bx, int bz,
            int cx, int cz, int dx, int dz
    ) {
        int o1 = orientation(ax, az, bx, bz, cx, cz);
        int o2 = orientation(ax, az, bx, bz, dx, dz);
        int o3 = orientation(cx, cz, dx, dz, ax, az);
        int o4 = orientation(cx, cz, dx, dz, bx, bz);
        if (o1 == 0 || o2 == 0 || o3 == 0 || o4 == 0) {
            // Collinear or endpoint-on-segment: treat as boundary contact, not interior overlap.
            return false;
        }
        return o1 != o2 && o3 != o4;
    }

    private static int orientation(int ax, int az, int bx, int bz, int cx, int cz) {
        long v = (long) (bz - az) * (cx - bx) - (long) (bx - ax) * (cz - bz);
        if (v < 0) {
            return -1;
        }
        if (v > 0) {
            return 1;
        }
        return 0;
    }

    private static boolean onBoundary(int x, int z, List<BlockPos> vertices) {
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            BlockPos a = vertices.get(j);
            BlockPos b = vertices.get(i);
            if (onSegment(x, z, a.x(), a.z(), b.x(), b.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean onSegment(int px, int pz, int ax, int az, int bx, int bz) {
        // Collinear and within bounding box of the segment.
        long cross = (long) (bx - ax) * (pz - az) - (long) (bz - az) * (px - ax);
        if (cross != 0) {
            return false;
        }
        int minX = Math.min(ax, bx);
        int maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz);
        int maxZ = Math.max(az, bz);
        return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
    }


    @Override
    public String toString() {
        return "Boundary{polygon=" + polygon.size() + " verts, chunks=" + chunks.size() + '}';
    }
}
