package org.aincraft.towny.services;

import org.aincraft.towny.models.TownBlock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing town blocks (plots)
 */
public interface PlotService {

    /**
     * Create a new town block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param townName Town name
     * @return Created town block
     */
    TownBlock createTownBlock(int x, int z, String world, String townName);

    /**
     * Get a town block by coordinates
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return Town block if found
     */
    Optional<TownBlock> getTownBlock(int x, int z, String world);

    /**
     * Get a town block by ID
     * @param id Town block ID
     * @return Town block if found
     */
    Optional<TownBlock> getTownBlock(UUID id);

    /**
     * Update town block information
     * @param townBlock Town block to update
     * @return Updated town block
     */
    TownBlock updateTownBlock(TownBlock townBlock);

    /**
     * Delete a town block
     * @param id Town block ID
     * @return True if deleted successfully
     */
    boolean deleteTownBlock(UUID id);

    /**
     * Get all town blocks
     * @return List of all town blocks
     */
    List<TownBlock> getAllTownBlocks();

    /**
     * Get town blocks in a specific town
     * @param townName Town name
     * @return List of town blocks in the town
     */
    List<TownBlock> getTownBlocksInTown(String townName);

    /**
     * Get town blocks in a specific world
     * @param world World name
     * @return List of town blocks in the world
     */
    List<TownBlock> getTownBlocksInWorld(String world);

    /**
     * Get town blocks owned by a specific resident
     * @param residentUuid Resident UUID
     * @return List of owned town blocks
     */
    List<TownBlock> getTownBlocksOwnedBy(UUID residentUuid);

    /**
     * Check if a town block exists at coordinates
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if town block exists
     */
    boolean townBlockExists(int x, int z, String world);

    /**
     * Claim a town block for a town
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param townName Town name
     * @return True if claimed successfully
     */
    boolean claimTownBlock(int x, int z, String world, String townName);

    /**
     * Unclaim a town block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if unclaimed successfully
     */
    boolean unclaimTownBlock(int x, int z, String world);

    /**
     * Set owner of a town block
     * @param id Town block ID
     * @param ownerUuid Owner UUID (null for town-owned)
     * @return True if set successfully
     */
    boolean setTownBlockOwner(UUID id, UUID ownerUuid);

    /**
     * Get town blocks in a radius around coordinates
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Radius in blocks
     * @param world World name
     * @return List of town blocks in radius
     */
    List<TownBlock> getTownBlocksInRadius(int centerX, int centerZ, int radius, String world);

    /**
     * Get town blocks by type
     * @param plotType Plot type
     * @return List of town blocks with specified type
     */
    List<TownBlock> getTownBlocksByType(String plotType);

    /**
     * Get town blocks with no owner (town-owned)
     * @param townName Town name
     * @return List of town-owned town blocks
     */
    List<TownBlock> getTownOwnedBlocks(String townName);

    /**
     * Get town count for a town
     * @param townName Town name
     * @return Number of town blocks
     */
    int getTownBlockCount(String townName);

    /**
     * Set plot type for a town block
     * @param id Town block ID
     * @param plotType Plot type
     * @return True if set successfully
     */
    boolean setPlotType(UUID id, String plotType);

    /**
     * Get town blocks at specific chunk coordinates
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param world World name
     * @return List of town blocks in chunk
     */
    List<TownBlock> getTownBlocksInChunk(int chunkX, int chunkZ, String world);
}