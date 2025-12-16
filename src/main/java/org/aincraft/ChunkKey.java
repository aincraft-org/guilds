package org.aincraft;

import org.bukkit.Chunk;

import java.util.Objects;

/**
 * Immutable identifier for a chunk, consisting of world name and chunk coordinates.
 */
public record ChunkKey(String world, int x, int z) {

    public ChunkKey {
        Objects.requireNonNull(world, "World cannot be null");
    }

    /**
     * Creates a ChunkKey from a Bukkit Chunk.
     */
    public static ChunkKey from(Chunk chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + z;
    }
}
