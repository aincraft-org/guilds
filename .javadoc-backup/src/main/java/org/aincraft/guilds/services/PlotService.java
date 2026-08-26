package org.aincraft.guilds.services;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing guild blocks (plots)
 */
public interface PlotService {

    /**
     * Create a new guild block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param guildName Guild name
     * @return Created guild block
     */
    GuildBlock createGuildBlock(int x, int z, String world, String guildName);

    /**
     * Get a guild block by coordinates
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return Guild block if found
     */
    Optional<GuildBlock> getGuildBlock(int x, int z, String world);

    /**
     * Get a guild block by ID
     * @param id Guild block ID
     * @return Guild block if found
     */
    Optional<GuildBlock> getGuildBlock(UUID id);

    /**
     * Update guild block information
     * @param guildBlock Guild block to update
     * @return Updated guild block
     */
    GuildBlock updateGuildBlock(GuildBlock guildBlock);

    /**
     * Delete a guild block
     * @param id Guild block ID
     * @return True if deleted successfully
     */
    boolean deleteGuildBlock(UUID id);

    /**
     * Get all guild blocks
     * @return List of all guild blocks
     */
    List<GuildBlock> getAllGuildBlocks();

    /**
     * Get guild blocks in a specific guild
     * @param guildName Guild name
     * @return List of guild blocks in the guild
     */
    List<GuildBlock> getGuildBlocksInGuild(String guildName);

    /**
     * Get guild blocks in a specific world
     * @param world World name
     * @return List of guild blocks in the world
     */
    List<GuildBlock> getGuildBlocksInWorld(String world);

    /**
     * Get guild blocks owned by a specific resident
     * @param residentUuid Resident UUID
     * @return List of owned guild blocks
     */
    List<GuildBlock> getGuildBlocksOwnedBy(UUID residentUuid);

    /**
     * Check if a guild block exists at coordinates
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if guild block exists
     */
    boolean guildBlockExists(int x, int z, String world);

    /**
     * Claim a guild block for a guild
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param guildName Guild name
     * @return True if claimed successfully
     */
    boolean claimGuildBlock(int x, int z, String world, String guildName);

    /**
     * Unclaim a guild block
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if unclaimed successfully
     */
    boolean unclaimGuildBlock(int x, int z, String world);

    /**
     * Set owner of a guild block
     * @param id Guild block ID
     * @param ownerUuid Owner UUID (null for guild-owned)
     * @return True if set successfully
     */
    boolean setGuildBlockOwner(UUID id, UUID ownerUuid);

    /**
     * Get guild blocks in a radius around coordinates
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Radius in blocks
     * @param world World name
     * @return List of guild blocks in radius
     */
    List<GuildBlock> getGuildBlocksInRadius(int centerX, int centerZ, int radius, String world);

    /**
     * Get guild blocks by type
     * @param plotType Plot type
     * @return List of guild blocks with specified type
     */
    List<GuildBlock> getGuildBlocksByType(String plotType);

    /**
     * Get guild blocks with no owner (guild-owned)
     * @param guildName Guild name
     * @return List of guild-owned guild blocks
     */
    List<GuildBlock> getGuildOwnedBlocks(String guildName);

    /**
     * Get guild count for a guild
     * @param guildName Guild name
     * @return Number of guild blocks
     */
    int getGuildBlockCount(String guildName);

    /**
     * Set plot type for a guild block
     * @param id Guild block ID
     * @param plotType Plot type
     * @return True if set successfully
     */
    boolean setPlotType(UUID id, String plotType);

    /**
     * Get guild blocks at specific chunk coordinates
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param world World name
     * @return List of guild blocks in chunk
     */
    List<GuildBlock> getGuildBlocksInChunk(int chunkX, int chunkZ, String world);

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
     * @param guildName Guild name (optional, can be null for all guilds)
     * @return List of plots for sale
     */
    List<GuildBlock> getPlotsForSale(String guildName);

    /**
     * Get plots owned by a specific resident
     * @param residentUuid Resident UUID
     * @return List of owned plots
     */
    List<GuildBlock> getPlotsOwnedByResident(UUID residentUuid);

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
     * @param targetType Target type (resident, guild, all, etc.)
     * @param targetId Target ID (resident UUID, guild name, etc.)
     * @param permissionFlag Permission flag to grant
     * @param grantedBy Who granted this permission
     * @return True if granted successfully
     */
    boolean grantPlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag, UUID grantedBy);

    /**
     * Revoke a permission from a specific target for a plot
     * @param plotId Plot ID
     * @param targetType Target type (resident, guild, all, etc.)
     * @param targetId Target ID (resident UUID, guild name, etc.)
     * @param permissionFlag Permission flag to revoke
     * @return True if revoked successfully
     */
    boolean revokePlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag);

    // Utility methods

    /**
     * Get the guild block at the specified player location
     * @param world World name
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Guild block at location if found
     */
    Optional<GuildBlock> getGuildBlockAtLocation(String world, int blockX, int blockZ);

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