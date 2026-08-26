package dev.mintychochip.territory.model;

/**
 * Minecraft chunk coordinates (16×16 blocks).
 *
 * @param chunkX chunk X coordinate
 * @param chunkZ chunk Z coordinate
 */
public record ChunkPos(int chunkX, int chunkZ) {

    /** Converts block coordinates to their containing chunk.
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return the containing chunk
     */
    public static ChunkPos fromBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    /** Northwest (min X/Z) block corner of this chunk.
     * @return the minimum block corner
     */
    public BlockPos minBlock() {
        return new BlockPos(chunkX * 16, chunkZ * 16);
    }

    /** Returns the canonical textual representation.
     * @return a string containing the chunk coordinates
     */
    @Override
    public String toString() {
        return "ChunkPos{cx=" + chunkX + ", cz=" + chunkZ + '}';
    }
}
