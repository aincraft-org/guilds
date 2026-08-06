package org.aincraft.guilds.services;

import com.azoth.territory.model.GovernmentForm;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing alliances
 */
public interface AllianceService {

    /**
     * Get a alliance by name
     * @param name Alliance name
     * @return Alliance if found
     */
    Optional<Alliance> getAlliance(String name);

    /**
     * Get a alliance by ID
     * @param id Alliance ID
     * @return Alliance if found
     */
    Optional<Alliance> getAllianceById(String id);

    /**
     * Get all alliances
     * @return List of all alliances
     */
    List<Alliance> getAllAlliances();

    /**
     * Create a new alliance
     * @param name Alliance name
     * @param capitalGuild Capital guild
     * @param kingUuid King's UUID
     */
    void createAlliance(String name, Guild capitalGuild, UUID kingUuid);

    /**
     * Delete a alliance
     * @param name Alliance name
     */
    void deleteAlliance(String name);

    /**
     * Get an alliance's governance form (the government form driving
     * permission semantics: ANARCHY/MONARCHY/OLIGARCHY/DEMOCRACY).
     * @param allianceId Alliance ID
     * @return The stored form, or {@code MONARCHY} when unknown
     */
    GovernmentForm getGovernanceForm(String allianceId);

    /**
     * Add a guild to a alliance
     * @param alliance Alliance to add guild to
     * @param guildId Guild ID to add
     */
    void addGuild(Alliance alliance, String guildId);

    /**
     * Remove a guild from a alliance
     * @param alliance Alliance to remove guild from
     * @param guildId Guild ID to remove
     */
    void removeGuild(Alliance alliance, String guildId);

    /**
     * Set the king of a alliance
     * @param alliance Alliance to update
     * @param newKing New king's UUID
     */
    void setKing(Alliance alliance, UUID newKing);

    /**
     * Add a minister to a alliance
     * @param alliance Alliance to add minister to
     * @param minister Minister UUID to add
     */
    void addMinister(Alliance alliance, UUID minister);

    /**
     * Remove a minister from a alliance
     * @param alliance Alliance to remove minister from
     * @param minister Minister UUID to remove
     */
    void removeMinister(Alliance alliance, UUID minister);

    /**
     * Add a alliance as an ally
     * @param alliance Alliance to add ally to
     * @param otherAlliance Other alliance name to ally with
     */
    void addAlly(Alliance alliance, String otherAlliance);

    /**
     * Remove a alliance as an ally
     * @param alliance Alliance to remove ally from
     * @param otherAlliance Other alliance name to remove as ally
     */
    void removeAlly(Alliance alliance, String otherAlliance);

    /**
     * Add a alliance as an enemy
     * @param alliance Alliance to add enemy to
     * @param otherAlliance Other alliance name to declare enemy
     */
    void addEnemy(Alliance alliance, String otherAlliance);

    /**
     * Remove a alliance as an enemy
     * @param alliance Alliance to remove enemy from
     * @param otherAlliance Other alliance name to remove as enemy
     */
    void removeEnemy(Alliance alliance, String otherAlliance);

    /**
     * Set the tax rate for a alliance
     * @param alliance Alliance to update
     * @param rate New tax rate (0-100)
     */
    void setTaxRate(Alliance alliance, double rate);

    /**
     * Set whether a alliance is open for new guilds to join
     * @param alliance Alliance to update
     * @param open True if open, false if closed
     */
    void setOpen(Alliance alliance, boolean open);

    /**
     * Update alliance information
     * @param alliance Alliance to update
     */
    void updateAlliance(Alliance alliance);

    /**
     * Save alliance to database
     * @param alliance Alliance to save
     */
    void saveAlliance(Alliance alliance);
}