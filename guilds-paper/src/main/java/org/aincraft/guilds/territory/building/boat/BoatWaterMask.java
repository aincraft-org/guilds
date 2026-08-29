package org.aincraft.guilds.territory.building.boat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable two-dimensional water mask for one chunk.
 *
 * <p>Cells use world block coordinates. The mask intentionally contains no
 * Bukkit references, so it can safely be handed to a worker thread.</p>
 */
public final class BoatWaterMask {
    public static final int CHUNK_SIZE = 16;

    private final int chunkX;
    private final int chunkZ;
    private final Set<Cell> navigableSurfaceCells;

    public BoatWaterMask(int chunkX, int chunkZ, Collection<Cell> navigableSurfaceCells) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Objects.requireNonNull(navigableSurfaceCells, "navigableSurfaceCells");
        Chunk declaredChunk = new Chunk(chunkX, chunkZ);
        LinkedHashSet<Cell> copy = new LinkedHashSet<>();
        for (Cell cell : navigableSurfaceCells) {
            Objects.requireNonNull(cell, "navigable surface cell");
            if (!declaredChunk.equals(cell.chunk())) {
                throw new IllegalArgumentException("water cell does not belong to mask chunk");
            }
            copy.add(cell);
        }
        this.navigableSurfaceCells = Set.copyOf(copy);
    }

    public BoatWaterMask(int chunkX, int chunkZ, Cell... navigableSurfaceCells) {
        this(chunkX, chunkZ, Set.of(navigableSurfaceCells));
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public Set<Cell> navigableSurfaceCells() {
        return navigableSurfaceCells;
    }

    /** Alias useful to callers that treat a mask as a set of cells. */
    public Set<Cell> cells() {
        return navigableSurfaceCells;
    }

    public boolean contains(Cell cell) {
        return navigableSurfaceCells.contains(cell);
    }

    public boolean contains(int x, int y, int z) {
        return contains(new Cell(x, y, z));
    }

    public boolean isEmpty() {
        return navigableSurfaceCells.isEmpty();
    }

    public Chunk chunk() {
        return new Chunk(chunkX, chunkZ);
    }

    public record Cell(int x, int y, int z) {
        public Chunk chunk() {
            return new Chunk(Math.floorDiv(x, CHUNK_SIZE), Math.floorDiv(z, CHUNK_SIZE));
        }
    }

    public record Chunk(int x, int z) {
    }
}
