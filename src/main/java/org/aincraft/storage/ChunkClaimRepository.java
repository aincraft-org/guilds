package org.aincraft.storage;

import org.aincraft.ChunkKey;
import org.aincraft.map.ChunkClaimData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing chunk claims.
 */
public interface ChunkClaimRepository {
    /**
     * Claims a chunk for a guild.
     *
     * @param chunk the chunk to claim
     * @param guildId the guild claiming the chunk
     * @param claimedBy the player who claimed it
     * @return true if claimed successfully, false if already claimed
     */
    boolean claim(ChunkKey chunk, String guildId, UUID claimedBy);

    /**
     * Unclaims a chunk.
     *
     * @param chunk the chunk to unclaim
     * @param guildId the guild that owns the chunk (for verification)
     * @return true if unclaimed successfully, false if not owned by guild
     */
    boolean unclaim(ChunkKey chunk, String guildId);

    /**
     * Unclaims all chunks owned by a guild.
     */
    void unclaimAll(String guildId);

    /**
     * Gets the guild that owns a chunk.
     *
     * @param chunk the chunk to check
     * @return the guild ID if claimed, empty otherwise
     */
    Optional<String> getOwner(ChunkKey chunk);

    /**
     * Gets all chunks owned by a guild.
     *
     * @param guildId the guild ID
     * @return list of chunk keys
     */
    List<ChunkKey> getGuildChunks(String guildId);

    /**
     * Gets the number of chunks owned by a guild.
     *
     * @param guildId the guild ID
     * @return the chunk count
     */
    int getChunkCount(String guildId);

    /**
     * Gets ownership data for multiple chunks in a single query.
     *
     * @param chunks list of chunks to query
     * @return map of chunk to claim data (excludes unclaimed chunks)
     */
    Map<ChunkKey, ChunkClaimData> getOwnersForChunks(List<ChunkKey> chunks);
}
