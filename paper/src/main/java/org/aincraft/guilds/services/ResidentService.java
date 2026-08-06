package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Resident;

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
     * Get residents in a specific town
     * @param townName Town name
     * @return List of residents in the town
     */
    List<Resident> getResidentsInTown(String townName);

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