package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownBlock;

import java.util.Optional;

/**
 * Service for location-based queries (finding towns and plots at coordinates)
 * Extracted from PermissionService for single responsibility
 */
public interface LocationService {

    /**
     * Get the town at a specific location
     * @param x X coordinate (block coordinate)
     * @param z Z coordinate (block coordinate)
     * @param world World name
     * @return Optional containing the town if found, empty if wilderness
     */
    Optional<Town> getTownAtLocation(int x, int z, String world);

    /**
     * Get the town block (plot) at a specific location
     * @param x X coordinate (block coordinate)
     * @param z Z coordinate (block coordinate)
     * @param world World name
     * @return Optional containing the town block if found, empty if wilderness
     */
    Optional<TownBlock> getTownBlockAtLocation(int x, int z, String world);

    /**
     * Check if a location is in wilderness (not claimed by any town)
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if wilderness, false if in a town
     */
    boolean isWilderness(int x, int z, String world);
}
