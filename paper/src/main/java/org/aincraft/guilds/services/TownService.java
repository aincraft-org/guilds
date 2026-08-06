package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing towns
 */
public interface TownService {

    /**
     * Create a new town
     * @param name Town name
     * @param mayorUuid Mayor's UUID
     * @return Created town
     */
    Town createTown(String name, UUID mayorUuid);

    /**
     * Create a new town with a home block location
     * @param name Town name
     * @param mayorUuid Mayor's UUID
     * @param homeBlockLocation Location for the home block
     * @return Created town
     */
    Town createTown(String name, UUID mayorUuid, Location homeBlockLocation);

    /**
     * Get a town by name
     * @param name Town name
     * @return Town if found
     */
    Optional<Town> getTown(String name);

    /**
     * Get a town by UUID
     * @param uuid Town UUID
     * @return Town if found
     */
    Optional<Town> getTown(UUID uuid);

    /**
     * Get a town by ID (database ID)
     * @param townId Town ID
     * @return Town if found
     */
    Optional<Town> getTownById(String townId);

    /**
     * Update town information
     * @param town Town to update
     * @return Updated town
     */
    Town updateTown(Town town);

    /**
     * Delete a town
     * @param name Town name
     * @return True if deleted successfully
     */
    boolean deleteTown(String name);

    /**
     * Get all towns
     * @return List of all towns
     */
    List<Town> getAllTowns();

    /**
     * Get towns sorted by population
     * @return List of towns sorted by resident count
     */
    List<Town> getTownsByPopulation();

    /**
     * Get towns sorted by balance
     * @return List of towns sorted by balance
     */
    List<Town> getTownsByBalance();

    /**
     * Check if a town exists
     * @param name Town name
     * @return True if town exists
     */
    boolean townExists(String name);

    /**
     * Add a resident to a town
     * @param townName Town name
     * @param residentUuid Resident UUID
     * @return True if added successfully
     */
    boolean addResidentToTown(String townName, UUID residentUuid);

    /**
     * Remove a resident from a town
     * @param townName Town name
     * @param residentUuid Resident UUID
     * @return True if removed successfully
     */
    boolean removeResidentFromTown(String townName, UUID residentUuid);

    /**
     * Set town mayor
     * @param townName Town name
     * @param mayorUuid New mayor UUID
     * @return True if set successfully
     */
    boolean setTownMayor(String townName, UUID mayorUuid);

    /**
     * Add town assistant
     * @param townName Town name
     * @param assistantUuid Assistant UUID
     * @return True if added successfully
     */
    boolean addTownAssistant(String townName, UUID assistantUuid);

    /**
     * Remove town assistant
     * @param townName Town name
     * @param assistantUuid Assistant UUID
     * @return True if removed successfully
     */
    boolean removeTownAssistant(String townName, UUID assistantUuid);

    /**
     * Get town resident count
     * @param townName Town name
     * @return Number of residents
     */
    int getTownResidentCount(String townName);

    /**
     * Update town balance
     * @param townName Town name
     * @param amount Amount to add (can be negative)
     * @return New balance
     */
    double updateTownBalance(String townName, double amount);

    /**
     * Get towns that are open for new residents
     * @return List of open towns
     */
    List<Town> getOpenTowns();

    /**
     * Search towns by name (partial match)
     * @param query Search query
     * @return List of matching towns
     */
    List<Town> searchTowns(String query);

    /**
     * Set town spawn location
     * @param townName Town name
     * @param location New spawn location
     * @return True if set successfully
     */
    boolean setTownSpawn(String townName, Location location);

    /**
     * Get town spawn location
     * @param townName Town name
     * @return Spawn location if found
     */
    Optional<Location> getTownSpawn(String townName);

    /**
     * Check if a player can teleport to a town spawn
     * @param playerUuid Player UUID
     * @param townName Town name
     * @return True if player can teleport
     */
    boolean canTeleportToSpawn(UUID playerUuid, String townName);

    // Town level system methods

    /**
     * Get towns sorted by level (highest first)
     * @return List of towns sorted by level
     */
    List<Town> getTownsByLevel();

    /**
     * Get towns by level range
     * @param minLevel Minimum level (inclusive)
     * @param maxLevel Maximum level (inclusive)
     * @return List of towns within the level range
     */
    List<Town> getTownsByLevelRange(int minLevel, int maxLevel);

    /**
     * Get towns with minimum level
     * @param minimumLevel Minimum level
     * @return List of towns with at least the specified level
     */
    List<Town> getTownsByMinimumLevel(int minimumLevel);

    /**
     * Get towns sorted by tech points (highest first)
     * @return List of towns sorted by tech points
     */
    List<Town> getTownsByTechPoints();

    /**
     * Get total tech points across all towns
     * @return Total tech points
     */
    int getTotalTechPoints();

    /**
     * Get town statistics including level information
     * @return Town statistics
     */
    TownStatistics getTownStatistics();

    /**
     * Update town level and tech points
     * @param townName Town name
     * @param newLevel New level
     * @param techPoints Tech points to add
     * @return True if updated successfully
     */
    boolean updateTownLevel(String townName, int newLevel, int techPoints);

    /**
     * Update town upgrade progress
     * @param townName Town name
     * @param upgradeProgress Upgrade progress map
     * @return True if updated successfully
     */
    boolean updateTownUpgradeProgress(String townName, java.util.Map<String, Integer> upgradeProgress);

    /**
     * Get towns ranked by various criteria
     * @param criteria Ranking criteria (level, residents, balance, techPoints)
     * @param limit Maximum number of towns to return
     * @return List of ranked towns
     */
    List<Town> getRankedTowns(String criteria, int limit);

    /**
     * Get top level towns
     * @param limit Maximum number of towns to return
     * @return List of top level towns
     */
    List<Town> getTopLevelTowns(int limit);

    /**
     * Get towns that can upgrade to the next level
     * @return List of towns ready for upgrade
     */
    List<Town> getTownsReadyForUpgrade();

    /**
     * Get average town level across all towns
     * @return Average town level
     */
    double getAverageTownLevel();

    // Town toggle system methods

    /**
     * Toggle a specific town permission setting
     * @param townName Town name
     * @param permissionType Permission type to toggle (pvp, fire, explosions, mobs, public)
     * @param playerUuid UUID of the player requesting the toggle
     * @return True if toggle was successful, false otherwise
     */
    boolean toggleTownPermission(String townName, String permissionType, UUID playerUuid);

    /**
     * Get all current toggle states for a town
     * @param townName Town name
     * @return Map of toggle names to their current states, empty if town not found
     */
    java.util.Map<String, Boolean> getTownToggles(String townName);

    /**
     * Set a specific toggle state for a town
     * @param townName Town name
     * @param permissionType Permission type to set (pvp, fire, explosions, mobs, public)
     * @param value New value for the toggle
     * @param playerUuid UUID of the player requesting the change
     * @return True if toggle was set successfully, false otherwise
     */
    boolean setTownToggle(String townName, String permissionType, boolean value, UUID playerUuid);

    /**
     * Get the current state of a specific toggle for a town
     * @param townName Town name
     * @param permissionType Permission type to check (pvp, fire, explosions, mobs, public)
     * @return Toggle state, or false if town or toggle type not found
     */
    boolean getTownToggle(String townName, String permissionType);

    /**
     * Statistics about towns in the system
     */
    class TownStatistics {
        private final int totalTowns;
        private final int averageLevel;
        private final int maxLevel;
        private final int totalTechPoints;
        private final int totalResidents;
        private final double totalBalance;
        private final java.util.Map<String, Integer> levelDistribution;

        public TownStatistics(int totalTowns, int averageLevel, int maxLevel,
                             int totalTechPoints, int totalResidents, double totalBalance,
                             java.util.Map<String, Integer> levelDistribution) {
            this.totalTowns = totalTowns;
            this.averageLevel = averageLevel;
            this.maxLevel = maxLevel;
            this.totalTechPoints = totalTechPoints;
            this.totalResidents = totalResidents;
            this.totalBalance = totalBalance;
            this.levelDistribution = levelDistribution;
        }

        public int getTotalTowns() { return totalTowns; }
        public int getAverageLevel() { return averageLevel; }
        public int getMaxLevel() { return maxLevel; }
        public int getTotalTechPoints() { return totalTechPoints; }
        public int getTotalResidents() { return totalResidents; }
        public double getTotalBalance() { return totalBalance; }
        public java.util.Map<String, Integer> getLevelDistribution() { return levelDistribution; }
    }
}