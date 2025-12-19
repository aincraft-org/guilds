package org.aincraft.project.storage;

import org.aincraft.project.ProjectDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisting project pools to database.
 * Handles storage and retrieval of guild project pools, which are refreshed every 24 hours.
 */
public interface GuildProjectPoolRepository {

    /**
     * Saves a complete project pool for a guild.
     * Persists all projects generated for the current pool period.
     *
     * @param guildId the guild ID
     * @param projects the list of projects in the pool
     * @param poolGenerationTime the timestamp when this pool was generated
     */
    void savePool(UUID guildId, List<ProjectDefinition> projects, long poolGenerationTime);

    /**
     * Retrieves all projects in a guild's pool.
     *
     * @param guildId the guild ID
     * @return list of project definitions in the pool, or empty list if no pool exists
     */
    List<ProjectDefinition> getPool(UUID guildId);

    /**
     * Deletes all projects in a guild's pool.
     *
     * @param guildId the guild ID
     */
    void deletePoolByGuildId(UUID guildId);

    /**
     * Gets the timestamp of the last pool generation for a guild.
     *
     * @param guildId the guild ID
     * @return Optional containing the timestamp, or empty if no pool exists
     */
    Optional<Long> getLastPoolGenerationTime(UUID guildId);

    /**
     * Sets the guild creation timestamp, used as anchor for 24h refresh cycles.
     *
     * @param guildId the guild ID
     * @param timestamp the guild creation timestamp in milliseconds
     */
    void setGuildCreatedAt(UUID guildId, long timestamp);

    /**
     * Gets the guild creation timestamp.
     *
     * @param guildId the guild ID
     * @return Optional containing the timestamp, or empty if not set
     */
    Optional<Long> getGuildCreatedAt(UUID guildId);
}
