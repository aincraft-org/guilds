package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Guild;

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
     * @param capitalGuild Capital guild
     * @param kingUuid King's UUID
     */
    void createNation(String name, Guild capitalGuild, UUID kingUuid);

    /**
     * Delete a nation
     * @param name Nation name
     */
    void deleteNation(String name);

    /**
     * Add a guild to a nation
     * @param nation Nation to add guild to
     * @param guildId Guild ID to add
     */
    void addGuild(Nation nation, String guildId);

    /**
     * Remove a guild from a nation
     * @param nation Nation to remove guild from
     * @param guildId Guild ID to remove
     */
    void removeGuild(Nation nation, String guildId);

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
     * Set whether a nation is open for new guilds to join
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