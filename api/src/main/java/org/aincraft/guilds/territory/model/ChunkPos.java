package org.aincraft.guilds.territory.model;

/**
 * Minecraft chunk coordinates (16×16 blocks).
 */
public record ChunkPos(int chunkX, int chunkZ) {

    /** Converts block coordinates to their containing chunk. */
    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    /** Northwest (min X/Z) block corner of this chunk. */
    public BlockPos minBlock() {
        return new BlockPos(chunkX * 16, chunkZ * 16);
    }

    @Override
    public String toString() {
        return "ChunkPos{cx=" + chunkX + ", cz=" + chunkZ + '}';
    }
}
