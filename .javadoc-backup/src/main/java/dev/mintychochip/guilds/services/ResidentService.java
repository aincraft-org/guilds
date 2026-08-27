package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.Resident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing residents
 */
public interface ResidentService {

    /**
     * Create a new resident
     * @param uuid Player UUID
     * @param name Player name
     * @return Created resident
     */
    Resident createResident(UUID uuid, String name);

    /**
     * Get a resident by UUID
     * @param uuid Player UUID
     * @return Resident if found
     */
    Optional<Resident> getResident(UUID uuid);

    /**
     * Get a resident by name
     * @param name Player name
     * @return Resident if found
     */
    Optional<Resident> getResident(String name);

    /**
     * Update resident information
     * @param resident Resident to update
     * @return Updated resident
     */
    Resident updateResident(Resident resident);

    /**
     * Delete a resident
     * @param uuid Player UUID
     * @return True if deleted successfully
     */
    boolean deleteResident(UUID uuid);

    /**
     * Get all residents
     * @return List of all residents
     */
    List<Resident> getAllResidents();

    /**
     * Get residents in a specific guild
     * @param guildName Guild name
     * @return List of residents in the guild
     */
    List<Resident> getResidentsInGuild(String guildName);

    /**
     * Search residents by case-insensitive name prefix (offline suggestions).
     * @param prefix Name prefix
     * @param limit Maximum results
     * @return Residents whose name starts with the prefix, ordered by name
     */
    List<Resident> searchResidents(String prefix, int limit);

    /**
     * Check if a resident exists
     * @param uuid Player UUID
     * @return True if resident exists
     */
    boolean residentExists(UUID uuid);

    /**
     * Get online residents count
     * @return Number of online residents
     */
    int getOnlineResidentsCount();

    /**
     * Update resident's last online time
     * @param uuid Player UUID
     */
    void updateLastOnline(UUID uuid);

    /**
     * Set resident's online status
     * @param uuid Player UUID
     * @param online Online status
     */
    void setOnlineStatus(UUID uuid, boolean online);
}