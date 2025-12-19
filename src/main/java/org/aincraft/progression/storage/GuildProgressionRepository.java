package org.aincraft.progression.storage;

import org.aincraft.progression.GuildProgression;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for persisting guild progression data.
 * Single Responsibility: Data access abstraction for guild progression.
 * Open/Closed: Implementations can extend without modifying interface.
 */
public interface GuildProgressionRepository {
    /**
     * Saves or updates a guild's progression state.
     *
     * @param progression the progression to save (cannot be null)
     */
    void save(GuildProgression progression);

    /**
     * Finds a guild's progression by guild ID.
     *
     * @param guildId the guild ID (cannot be null)
     * @return the progression if found, empty otherwise
     */
    Optional<GuildProgression> findByGuildId(UUID guildId);

    /**
     * Deletes a guild's progression data.
     *
     * @param guildId the guild ID (cannot be null)
     */
    void delete(UUID guildId);

    /**
     * Records an XP contribution from a player to their guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param xpAmount the amount of XP contributed
     */
    void recordContribution(UUID guildId, UUID playerId, long xpAmount);

    /**
     * Gets the top XP contributors for a guild.
     *
     * @param guildId the guild ID
     * @param limit maximum number of contributors to return
     * @return map of player UUID to total XP contributed, sorted descending
     */
    Map<UUID, Long> getTopContributors(UUID guildId, int limit);

    /**
     * Gets the total XP contributed by a specific player to their guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the total XP contributed, or 0 if no record found
     */
    long getPlayerContribution(UUID guildId, UUID playerId);

    /**
     * Deletes all contribution records for a guild.
     *
     * @param guildId the guild ID
     */
    void deleteContributions(UUID guildId);
}
