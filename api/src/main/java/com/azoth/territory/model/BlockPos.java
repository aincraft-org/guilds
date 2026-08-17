package com.azoth.territory.model;

/**
 * Immutable block-space XZ position (Y ignored for 2D territory geometry).
 */
public record BlockPos(int x, int z) {

    public ChunkPos toChunk() {
        return ChunkPos.fromBlock(x, z);
    }

    @Override
    public String toString() {
        return "BlockPos{x=" + x + ", z=" + z + '}';
    }
}
