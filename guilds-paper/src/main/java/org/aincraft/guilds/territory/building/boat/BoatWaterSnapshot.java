package org.aincraft.guilds.territory.building.boat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable main-thread capture of one loaded chunk's boat geometry. */
public record BoatWaterSnapshot(
        UUID worldId,
        int chunkX,
        int chunkZ,
        BoatWaterMask waterMask,
        Set<BoatWaterMask.Cell> endpointClearSpaceCells
) {
    public BoatWaterSnapshot {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(waterMask, "waterMask");
        if (waterMask.chunkX() != chunkX || waterMask.chunkZ() != chunkZ) {
            throw new IllegalArgumentException("water mask chunk does not match snapshot chunk");
        }
        Objects.requireNonNull(endpointClearSpaceCells, "endpointClearSpaceCells");
        BoatWaterMask.Chunk declaredChunk = new BoatWaterMask.Chunk(chunkX, chunkZ);
        LinkedHashSet<BoatWaterMask.Cell> copy = new LinkedHashSet<>();
        for (BoatWaterMask.Cell cell : endpointClearSpaceCells) {
            Objects.requireNonNull(cell, "endpoint clear-space cell");
            if (!declaredChunk.equals(cell.chunk())) {
                throw new IllegalArgumentException(
                        "endpoint clear-space cell does not belong to snapshot chunk");
            }
            copy.add(cell);
        }
        endpointClearSpaceCells = Set.copyOf(copy);
    }

    public BoatWaterSnapshot(UUID worldId, BoatWaterMask waterMask) {
        this(worldId, waterMask.chunkX(), waterMask.chunkZ(), waterMask,
                waterMask.navigableSurfaceCells());
    }
    public BoatWaterSnapshot(UUID worldId,
                             BoatWaterMask waterMask,
                             Collection<BoatWaterMask.Cell> endpointClearSpaceCells) {
        this(worldId, waterMask.chunkX(), waterMask.chunkZ(), waterMask,
                endpointClearSpaceCells);
    }


    public BoatWaterSnapshot(UUID worldId, int chunkX, int chunkZ,
                             BoatWaterMask waterMask,
                             Collection<BoatWaterMask.Cell> endpointClearSpaceCells) {
        this(worldId, chunkX, chunkZ, waterMask,
                Set.copyOf(Objects.requireNonNull(endpointClearSpaceCells, "endpointClearSpaceCells")));
    }

    public Set<BoatWaterMask.Cell> clearSpaceCells() {
        return endpointClearSpaceCells;
    }

    public boolean contains(BoatWaterMask.Cell cell) {
        return waterMask.contains(cell);
    }

    /**
     * An empty clear-space set means that this older/simple snapshot carries no
     * endpoint-specific assertions. Non-empty sets identify cells captured with
     * the configured clear boat-space height.
     */
    public boolean hasClearSpace(BoatWaterMask.Cell cell) {
        return endpointClearSpaceCells.isEmpty() || endpointClearSpaceCells.contains(cell);
    }

    public BoatWaterMask.Chunk chunk() {
        return new BoatWaterMask.Chunk(chunkX, chunkZ);
    }
}
