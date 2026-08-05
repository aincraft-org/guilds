package com.azoth.territory.model;

import java.util.Objects;

/**
 * Immutable block-space XZ position (Y ignored for 2D territory geometry).
 */
public final class BlockPos {
    private final int x;
    private final int z;

    public BlockPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public ChunkPos toChunk() {
        return ChunkPos.fromBlock(x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockPos that)) {
            return false;
        }
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "BlockPos{x=" + x + ", z=" + z + '}';
    }
}
