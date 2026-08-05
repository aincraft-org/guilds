package com.azoth.territory.model;

import java.util.Objects;

/**
 * Minecraft chunk coordinates (16×16 blocks).
 */
public final class ChunkPos {
    private final int chunkX;
    private final int chunkZ;

    public ChunkPos(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** Northwest (min X/Z) block corner of this chunk. */
    public BlockPos minBlock() {
        return new BlockPos(chunkX * 16, chunkZ * 16);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChunkPos that)) {
            return false;
        }
        return chunkX == that.chunkX && chunkZ == that.chunkZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return "ChunkPos{cx=" + chunkX + ", cz=" + chunkZ + '}';
    }
}
