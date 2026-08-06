package org.aincraft.towny.services;

/**
 * Service for managing town toggle settings (PvP, fire, mobs, explosions, public access)
 * Separated from PermissionService for single responsibility
 */
public interface TownToggleService {

    /**
     * Check if PvP is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if PvP is enabled, false otherwise (wilderness defaults to true)
     */
    boolean isPvpEnabledAtLocation(int x, int z, String world);

    /**
     * Check if fire spread is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if fire spread is enabled, false otherwise (wilderness defaults to false)
     */
    boolean isFireEnabledAtLocation(int x, int z, String world);

    /**
     * Check if explosions are enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if explosions are enabled, false otherwise (wilderness defaults to false)
     */
    boolean areExplosionsEnabledAtLocation(int x, int z, String world);

    /**
     * Check if mob spawning is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if mob spawning is enabled, false otherwise (wilderness defaults to true)
     */
    boolean areMobsEnabledAtLocation(int x, int z, String world);

    /**
     * Check if a town has public access at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if public access is enabled, false otherwise (wilderness defaults to true)
     */
    boolean isPublicAccessEnabledAtLocation(int x, int z, String world);

    /**
     * Get all toggle states for a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return Map of toggle states, empty if no town found at location
     */
    java.util.Map<String, Boolean> getTogglesAtLocation(int x, int z, String world);
}
