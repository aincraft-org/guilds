package org.aincraft.towny.services;

import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.models.Permission;

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

    // Plot claiming and ownership methods

    /**
     * Claim a plot for a resident (for personal ownership)
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if claimed successfully
     */
    boolean claimPlotForResident(UUID residentUuid, int x, int z, String world);

    /**
     * Buy a plot that is for sale
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @param price Price to pay
     * @return True if purchased successfully
     */
    boolean buyPlot(UUID residentUuid, UUID plotId, double price);

    /**
     * Set a plot for sale
     * @param plotId Plot ID
     * @param price Sale price (0 to remove from sale)
     * @param ownerUuid Current owner UUID (for verification)
     * @return True if set for sale successfully
     */
    boolean setPlotForSale(UUID plotId, double price, UUID ownerUuid);

    /**
     * Get plots that are for sale
     * @param townName Town name (optional, can be null for all towns)
     * @return List of plots for sale
     */
    List<TownBlock> getPlotsForSale(String townName);

    /**
     * Get plots owned by a specific resident
     * @param residentUuid Resident UUID
     * @return List of owned plots
     */
    List<TownBlock> getPlotsOwnedByResident(UUID residentUuid);

    // Plot permission management methods

    /**
     * Set a permission flag for a plot
     * @param plotId Plot ID
     * @param permissionFlag Permission flag to set
     * @param value Permission value (true to add, false to remove)
     * @return True if set successfully
     */
    boolean setPlotPermissionFlag(UUID plotId, int permissionFlag, boolean value);

    /**
     * Set multiple permission flags for a plot
     * @param plotId Plot ID
     * @param flags Permission flags to set (bitwise combination)
     * @return True if set successfully
     */
    boolean setPlotPermissionFlags(UUID plotId, int flags);

    /**
     * Add a permission flag to a plot
     * @param plotId Plot ID
     * @param permissionFlag Permission flag to add
     * @return True if added successfully
     */
    boolean addPlotPermissionFlag(UUID plotId, int permissionFlag);

    /**
     * Remove a permission flag from a plot
     * @param plotId Plot ID
     * @param permissionFlag Permission flag to remove
     * @return True if removed successfully
     */
    boolean removePlotPermissionFlag(UUID plotId, int permissionFlag);

    /**
     * Get plot-specific permissions from the permissions table
     * @param plotId Plot ID
     * @return List of plot permissions
     */
    List<Permission> getPlotPermissions(UUID plotId);

    /**
     * Grant a permission to a specific target for a plot
     * @param plotId Plot ID
     * @param targetType Target type (resident, town, all, etc.)
     * @param targetId Target ID (resident UUID, town name, etc.)
     * @param permissionFlag Permission flag to grant
     * @param grantedBy Who granted this permission
     * @return True if granted successfully
     */
    boolean grantPlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag, UUID grantedBy);

    /**
     * Revoke a permission from a specific target for a plot
     * @param plotId Plot ID
     * @param targetType Target type (resident, town, all, etc.)
     * @param targetId Target ID (resident UUID, town name, etc.)
     * @param permissionFlag Permission flag to revoke
     * @return True if revoked successfully
     */
    boolean revokePlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag);

    // Utility methods

    /**
     * Get the town block at the specified player location
     * @param world World name
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Town block at location if found
     */
    Optional<TownBlock> getTownBlockAtLocation(String world, int blockX, int blockZ);

    /**
     * Check if a resident can claim a plot at the specified location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can claim
     */
    boolean canResidentClaimPlot(UUID residentUuid, int x, int z, String world);

    /**
     * Check if a resident can afford to buy a plot
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @return True if can afford
     */
    boolean canResidentAffordPlot(UUID residentUuid, UUID plotId);
}