package org.aincraft.outpost;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;

/**
 * Repository interface for persisting outposts.
 * Single Responsibility: Define persistence contract for outposts.
 * Dependency Inversion: Clients depend on this abstraction, not implementations.
 */
public interface OutpostRepository {
    /**
     * Saves a new or updated outpost to the database.
     *
     * @param outpost the outpost to save (cannot be null)
     * @return true if saved successfully
     */
    boolean save(Outpost outpost);

    /**
     * Deletes an outpost by ID.
     *
     * @param outpostId the outpost ID (cannot be null)
     * @return true if deleted successfully
     */
    boolean delete(UUID outpostId);

    /**
     * Finds an outpost by ID.
     *
     * @param outpostId the outpost ID (cannot be null)
     * @return the outpost, or empty if not found
     */
    Optional<Outpost> findById(UUID outpostId);

    /**
     * Finds an outpost by guild and name.
     * Name comparison is case-insensitive.
     *
     * @param guildId the guild ID (cannot be null)
     * @param name the outpost name (cannot be null)
     * @return the outpost, or empty if not found
     */
    Optional<Outpost> findByGuildAndName(UUID guildId, String name);

    /**
     * Gets all outposts for a guild.
     *
     * @param guildId the guild ID (cannot be null)
     * @return list of outposts, empty list if none found
     */
    List<Outpost> findByGuild(UUID guildId);

    /**
     * Gets all outposts in a specific chunk.
     *
     * @param chunk the chunk location (cannot be null)
     * @return list of outposts, empty list if none found
     */
    List<Outpost> findByChunk(ChunkKey chunk);

    /**
     * Gets count of outposts for a guild.
     *
     * @param guildId the guild ID (cannot be null)
     * @return the count
     */
    int getCountByGuild(UUID guildId);

    /**
     * Deletes all outposts for a guild (used when guild is deleted).
     *
     * @param guildId the guild ID (cannot be null)
     * @return the number of outposts deleted
     */
    int deleteByGuild(UUID guildId);

    /**
     * Deletes all outposts in a specific chunk (used when chunk is unclaimed).
     *
     * @param chunk the chunk location (cannot be null)
     * @return the number of outposts deleted
     */
    int deleteByChunk(ChunkKey chunk);
}
