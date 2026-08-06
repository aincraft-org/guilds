package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Blueprint;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing guild blueprints.
 */
public interface BlueprintService {

    /**
     * Gets a blueprint by name.
     * @param name The blueprint name
     * @return Optional containing the blueprint if found
     */
    Optional<Blueprint> getBlueprint(String name);

    /**
     * Gets all blueprints for a guild.
     * @param guildId The guild ID
     * @return List of blueprints for the guild
     */
    List<Blueprint> getGuildBlueprints(String guildId);

    /**
     * Saves a new blueprint.
     * @param name The blueprint name
     * @param author The author UUID
     * @param guildId The guild ID
     * @param schematicData The serialized schematic data
     */
    void saveBlueprint(String name, UUID author, String guildId, byte[] schematicData);

    /**
     * Deletes a blueprint by name.
     * @param name The blueprint name to delete
     */
    void deleteBlueprint(String name);

    /**
     * Applies a blueprint to the specified location.
     * @param name The blueprint name to apply
     * @param location The location to paste the blueprint at
     * @return true if successful, false otherwise
     */
    boolean applyBlueprint(String name, Location location);
}