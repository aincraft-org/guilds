package org.aincraft.towny.services;

import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.Location;

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
}