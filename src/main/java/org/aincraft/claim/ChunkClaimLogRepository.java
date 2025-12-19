package org.aincraft.claim;

import java.util.List;
import java.util.UUID;
import org.aincraft.ChunkKey;

/**
 * Repository for managing chunk claim log entries.
 * Follows Repository pattern for data access abstraction.
 */
public interface ChunkClaimLogRepository {

    /**
     * Logs a chunk claim or unclaim action.
     *
     * @param entry the log entry to record
     */
    void log(ChunkClaimLog entry);

    /**
     * Finds log entries for a specific guild, ordered by timestamp descending (newest first).
     *
     * @param guildId the guild ID
     * @param limit maximum number of entries to return
     * @return list of log entries, newest first
     */
    List<ChunkClaimLog> findByGuildId(UUID guildId, int limit);

    /**
     * Finds log entries by a specific player, ordered by timestamp descending (newest first).
     *
     * @param playerId the player UUID
     * @param limit maximum number of entries to return
     * @return list of log entries, newest first
     */
    List<ChunkClaimLog> findByPlayer(UUID playerId, int limit);

    /**
     * Finds log entries for a specific chunk, ordered by timestamp descending (newest first).
     *
     * @param chunk the chunk key
     * @param limit maximum number of entries to return
     * @return list of log entries, newest first
     */
    List<ChunkClaimLog> findByChunk(ChunkKey chunk, int limit);

    /**
     * Deletes all log entries for a guild.
     * Called when a guild is deleted.
     *
     * @param guildId the guild ID
     */
    void deleteByGuildId(UUID guildId);
}
