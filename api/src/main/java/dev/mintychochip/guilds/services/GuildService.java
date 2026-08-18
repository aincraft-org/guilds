package dev.mintychochip.guilds.services;

import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing guilds
 */
public interface GuildService {

    /**
     * Create a new guild
     * @param name Guild name
     * @param mayorUuid Mayor's UUID
     * @return Created guild
     */
    Guild createGuild(String name, UUID mayorUuid);

    /**
     * Create a new guild with a home block location
     * @param name Guild name
     * @param mayorUuid Mayor's UUID
     * @param homeBlockLocation Location for the home block
     * @return Created guild
     */
    Guild createGuild(String name, UUID mayorUuid, Location homeBlockLocation);

    /**
     * Get a guild by name
     * @param name Guild name
     * @return Guild if found
     */
    Optional<Guild> getGuild(String name);

    /**
     * Get a guild by UUID
     * @param uuid Guild UUID
     * @return Guild if found
     */
    Optional<Guild> getGuild(UUID uuid);

    /**
     * Get a guild by ID (database ID)
     * @param guildId Guild ID
     * @return Guild if found
     */
    Optional<Guild> getGuildById(String guildId);

    /**
     * Update guild information
     * @param guild Guild to update
     * @return Updated guild
     */
    Guild updateGuild(Guild guild);

    /**
     * Delete a guild
     * @param name Guild name
     * @return True if deleted successfully
     */
    boolean deleteGuild(String name);

    /**
     * Get all guilds
     * @return List of all guilds
     */
    List<Guild> getAllGuilds();

    /**
     * Get guilds sorted by population
     * @return List of guilds sorted by resident count
     */
    List<Guild> getGuildsByPopulation();

    /**
     * Get guilds sorted by balance
     * @return List of guilds sorted by balance
     */
    List<Guild> getGuildsByBalance();

    /**
     * Check if a guild exists
     * @param name Guild name
     * @return True if guild exists
     */
    boolean guildExists(String name);

    /**
     * Get a guild's governance form (the government form driving permission
     * semantics: ANARCHY/MONARCHY/OLIGARCHY/DEMOCRACY).
     * @param guildId Guild ID
     * @return The stored form, or {@code MONARCHY} when unknown
     */
    GovernmentForm getGovernanceForm(String guildId);

    /**
     * Add a resident to a guild
     * @param guildName Guild name
     * @param residentUuid Resident UUID
     * @return True if added successfully
     */
    boolean addResidentToGuild(String guildName, UUID residentUuid);

    /**
     * Remove a resident from a guild
     * @param guildName Guild name
     * @param residentUuid Resident UUID
     * @return True if removed successfully
     */
    boolean removeResidentFromGuild(String guildName, UUID residentUuid);

    /**
     * Set guild mayor
     * @param guildName Guild name
     * @param mayorUuid New mayor UUID
     * @return True if set successfully
     */
    boolean setGuildMayor(String guildName, UUID mayorUuid);

    /**
     * Add guild assistant
     * @param guildName Guild name
     * @param assistantUuid Assistant UUID
     * @return True if added successfully
     */
    boolean addGuildAssistant(String guildName, UUID assistantUuid);

    /**
     * Remove guild assistant
     * @param guildName Guild name
     * @param assistantUuid Assistant UUID
     * @return True if removed successfully
     */
    boolean removeGuildAssistant(String guildName, UUID assistantUuid);

    /**
     * Get guild resident count
     * @param guildName Guild name
     * @return Number of residents
     */
    int getGuildResidentCount(String guildName);

    /**
     * Update guild balance
     * @param guildName Guild name
     * @param amount Amount to add (can be negative)
     * @return New balance
     */
    double updateGuildBalance(String guildName, double amount);

    /**
     * Get guilds that are open for new residents
     * @return List of open guilds
     */
    List<Guild> getOpenGuilds();

    /**
     * Search guilds by name (partial match)
     * @param query Search query
     * @return List of matching guilds
     */
    List<Guild> searchGuilds(String query);

    /**
     * Set guild spawn location
     * @param guildName Guild name
     * @param location New spawn location
     * @return True if set successfully
     */
    boolean setGuildSpawn(String guildName, Location location);

    /**
     * Get guild spawn location
     * @param guildName Guild name
     * @return Spawn location if found
     */
    Optional<Location> getGuildSpawn(String guildName);

    /**
     * Check if a player can teleport to a guild spawn
     * @param playerUuid Player UUID
     * @param guildName Guild name
     * @return True if player can teleport
     */
    boolean canTeleportToSpawn(UUID playerUuid, String guildName);

    // Guild level system methods

    /**
     * Get guilds sorted by level (highest first)
     * @return List of guilds sorted by level
     */
    List<Guild> getGuildsByLevel();

    /**
     * Get guilds by level range
     * @param minLevel Minimum level (inclusive)
     * @param maxLevel Maximum level (inclusive)
     * @return List of guilds within the level range
     */
    List<Guild> getGuildsByLevelRange(int minLevel, int maxLevel);

    /**
     * Get guilds with minimum level
     * @param minimumLevel Minimum level
     * @return List of guilds with at least the specified level
     */
    List<Guild> getGuildsByMinimumLevel(int minimumLevel);

    /**
     * Get guilds sorted by tech points (highest first)
     * @return List of guilds sorted by tech points
     */
    List<Guild> getGuildsByTechPoints();

    /**
     * Get total tech points across all guilds
     * @return Total tech points
     */
    int getTotalTechPoints();

    /**
     * Get guild statistics including level information
     * @return Guild statistics
     */
    GuildStatistics getGuildStatistics();

    /**
     * Update guild level and tech points
     * @param guildName Guild name
     * @param newLevel New level
     * @param techPoints Tech points to add
     * @return True if updated successfully
     */
    boolean updateGuildLevel(String guildName, int newLevel, int techPoints);

    /**
     * Update guild upgrade progress
     * @param guildName Guild name
     * @param upgradeProgress Upgrade progress map
     * @return True if updated successfully
     */
    boolean updateGuildUpgradeProgress(String guildName, java.util.Map<String, Integer> upgradeProgress);

    /**
     * Get guilds ranked by various criteria
     * @param criteria Ranking criteria (level, residents, balance, techPoints)
     * @param limit Maximum number of guilds to return
     * @return List of ranked guilds
     */
    List<Guild> getRankedGuilds(String criteria, int limit);

    /**
     * Get top level guilds
     * @param limit Maximum number of guilds to return
     * @return List of top level guilds
     */
    List<Guild> getTopLevelGuilds(int limit);

    /**
     * Get guilds that can upgrade to the next level
     * @return List of guilds ready for upgrade
     */
    List<Guild> getGuildsReadyForUpgrade();

    /**
     * Get average guild level across all guilds
     * @return Average guild level
     */
    double getAverageGuildLevel();

    // Guild toggle system methods

    /**
     * Toggle a specific guild permission setting
     * @param guildName Guild name
     * @param permissionType Permission type to toggle (pvp, fire, explosions, mobs, public)
     * @param playerUuid UUID of the player requesting the toggle
     * @return True if toggle was successful, false otherwise
     */
    boolean toggleGuildPermission(String guildName, String permissionType, UUID playerUuid);

    /**
     * Get all current toggle states for a guild
     * @param guildName Guild name
     * @return Map of toggle names to their current states, empty if guild not found
     */
    java.util.Map<String, Boolean> getGuildToggles(String guildName);

    /**
     * Set a specific toggle state for a guild
     * @param guildName Guild name
     * @param permissionType Permission type to set (pvp, fire, explosions, mobs, public)
     * @param value New value for the toggle
     * @param playerUuid UUID of the player requesting the change
     * @return True if toggle was set successfully, false otherwise
     */
    boolean setGuildToggle(String guildName, String permissionType, boolean value, UUID playerUuid);

    /**
     * Get the current state of a specific toggle for a guild
     * @param guildName Guild name
     * @param permissionType Permission type to check (pvp, fire, explosions, mobs, public)
     * @return Toggle state, or false if guild or toggle type not found
     */
    boolean getGuildToggle(String guildName, String permissionType);

    /**
     * Statistics about guilds in the system
     */
    class GuildStatistics {
        /** The total guilds. */
        private final int totalGuilds;
        /** The average level. */
        private final int averageLevel;
        /** The max level. */
        private final int maxLevel;
        /** The total tech points. */
        private final int totalTechPoints;
        /** The total residents. */
        private final int totalResidents;
        /** The total balance. */
        private final double totalBalance;
        /** The level distribution. */
        private final java.util.Map<String, Integer> levelDistribution;

        /**
         * Creates a new guild statistics instance.
         * @param totalGuilds the total guilds
         * @param averageLevel the average level
         * @param maxLevel the max level
         * @param totalTechPoints the total tech points
         * @param totalResidents the total residents
         * @param totalBalance the total balance
         * @param levelDistribution the level distribution
         */
        public GuildStatistics(int totalGuilds, int averageLevel, int maxLevel,
                             int totalTechPoints, int totalResidents, double totalBalance,
                             java.util.Map<String, Integer> levelDistribution) {
            this.totalGuilds = totalGuilds;
            this.averageLevel = averageLevel;
            this.maxLevel = maxLevel;
            this.totalTechPoints = totalTechPoints;
            this.totalResidents = totalResidents;
            this.totalBalance = totalBalance;
            this.levelDistribution = levelDistribution;
        }

        /**
         * Returns the total guilds.
         * @return the result
         */
        public int getTotalGuilds() { return totalGuilds; }
        /**
         * Returns the average level.
         * @return the result
         */
        public int getAverageLevel() { return averageLevel; }
        /**
         * Returns the max level.
         * @return the result
         */
        public int getMaxLevel() { return maxLevel; }
        /**
         * Returns the total tech points.
         * @return the result
         */
        public int getTotalTechPoints() { return totalTechPoints; }
        /**
         * Returns the total residents.
         * @return the result
         */
        public int getTotalResidents() { return totalResidents; }
        /**
         * Returns the total balance.
         * @return the result
         */
        public double getTotalBalance() { return totalBalance; }
        /**
         * Returns the level distribution.
         * @return the result
         */
        public java.util.Map<String, Integer> getLevelDistribution() { return levelDistribution; }
    }
}