package com.azoth.territory.model;

/**
 * Immutable block-space XZ position (Y ignored for 2D territory geometry).
 *
 * @param x block-space X coordinate
 * @param z block-space Z coordinate
 */
public record BlockPos(int x, int z) {

    /**
     * Converts this block position to its containing chunk.
     *
     * @return the containing chunk position
     */
    public ChunkPos toChunk() {
        return ChunkPos.fromBlock(x, z);
    }

    /**
     * Returns the canonical textual representation of this position.
     *
     * @return a string containing the X and Z coordinates
     */
    @Override
    public String toString() {
        return "BlockPos{x=" + x + ", z=" + z + '}';
    }
}
