package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;

import java.util.Optional;

/**
 * Service for location-based queries (finding guilds and plots at coordinates)
 * Extracted from PermissionService for single responsibility
 */
public interface LocationService {

    /**
     * Get the guild at a specific location
     * @param x X coordinate (block coordinate)
     * @param z Z coordinate (block coordinate)
     * @param world World name
     * @return Optional containing the guild if found, empty if wilderness
     */
    Optional<Guild> getGuildAtLocation(int x, int z, String world);

    /**
     * Get the guild block (plot) at a specific location
     * @param x X coordinate (block coordinate)
     * @param z Z coordinate (block coordinate)
     * @param world World name
     * @return Optional containing the guild block if found, empty if wilderness
     */
    Optional<GuildBlock> getGuildBlockAtLocation(int x, int z, String world);

    /**
     * Check if a location is in wilderness (not claimed by any guild)
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if wilderness, false if in a guild
     */
    boolean isWilderness(int x, int z, String world);
}
