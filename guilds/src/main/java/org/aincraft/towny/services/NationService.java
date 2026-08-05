package org.aincraft.towny.services;

import org.aincraft.towny.models.Nation;
import org.aincraft.towny.models.Town;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing nations
 */
public interface NationService {

    /**
     * Get a nation by name
     * @param name Nation name
     * @return Nation if found
     */
    Optional<Nation> getNation(String name);

    /**
     * Get a nation by ID
     * @param id Nation ID
     * @return Nation if found
     */
    Optional<Nation> getNationById(String id);

    /**
     * Get all nations
     * @return List of all nations
     */
    List<Nation> getAllNations();

    /**
     * Create a new nation
     * @param name Nation name
     * @param capitalTown Capital town
     * @param kingUuid King's UUID
     */
    void createNation(String name, Town capitalTown, UUID kingUuid);

    /**
     * Delete a nation
     * @param name Nation name
     */
    void deleteNation(String name);

    /**
     * Add a town to a nation
     * @param nation Nation to add town to
     * @param townId Town ID to add
     */
    void addTown(Nation nation, String townId);

    /**
     * Remove a town from a nation
     * @param nation Nation to remove town from
     * @param townId Town ID to remove
     */
    void removeTown(Nation nation, String townId);

    /**
     * Set the king of a nation
     * @param nation Nation to update
     * @param newKing New king's UUID
     */
    void setKing(Nation nation, UUID newKing);

    /**
     * Add a minister to a nation
     * @param nation Nation to add minister to
     * @param minister Minister UUID to add
     */
    void addMinister(Nation nation, UUID minister);

    /**
     * Remove a minister from a nation
     * @param nation Nation to remove minister from
     * @param minister Minister UUID to remove
     */
    void removeMinister(Nation nation, UUID minister);

    /**
     * Add a nation as an ally
     * @param nation Nation to add ally to
     * @param otherNation Other nation name to ally with
     */
    void addAlly(Nation nation, String otherNation);

    /**
     * Remove a nation as an ally
     * @param nation Nation to remove ally from
     * @param otherNation Other nation name to remove as ally
     */
    void removeAlly(Nation nation, String otherNation);

    /**
     * Add a nation as an enemy
     * @param nation Nation to add enemy to
     * @param otherNation Other nation name to declare enemy
     */
    void addEnemy(Nation nation, String otherNation);

    /**
     * Remove a nation as an enemy
     * @param nation Nation to remove enemy from
     * @param otherNation Other nation name to remove as enemy
     */
    void removeEnemy(Nation nation, String otherNation);

    /**
     * Set the tax rate for a nation
     * @param nation Nation to update
     * @param rate New tax rate (0-100)
     */
    void setTaxRate(Nation nation, double rate);

    /**
     * Set whether a nation is open for new towns to join
     * @param nation Nation to update
     * @param open True if open, false if closed
     */
    void setOpen(Nation nation, boolean open);

    /**
     * Update nation information
     * @param nation Nation to update
     */
    void updateNation(Nation nation);

    /**
     * Save nation to database
     * @param nation Nation to save
     */
    void saveNation(Nation nation);
}