package org.aincraft.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.ChunkKey;
import org.aincraft.ClaimResult;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.claim.ChunkClaimLog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Facade service for territory/chunk operations.
 * Delegates to GuildService for all operations.
 */
@Singleton
public class TerritoryService {
    private final GuildService guildService;

    @Inject
    public TerritoryService(GuildService guildService) {
        this.guildService = Objects.requireNonNull(guildService);
    }

    /**
     * Claims a chunk for a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param chunk the chunk to claim
     * @return the claim result
     */
    public ClaimResult claimChunk(UUID guildId, UUID playerId, ChunkKey chunk) {
        return guildService.claimChunk(guildId, playerId, chunk);
    }

    /**
     * Admin claims a chunk for a guild (bypasses permission checks).
     *
     * @param guildId the guild ID
     * @param chunk the chunk to claim
     */
    public void adminClaimChunk(UUID guildId, ChunkKey chunk) {
        guildService.adminClaimChunk(guildId, chunk);
    }

    /**
     * Unclaims a chunk.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param chunk the chunk to unclaim
     * @return true if unclaimed successfully
     */
    public boolean unclaimChunk(UUID guildId, UUID playerId, ChunkKey chunk) {
        return guildService.unclaimChunk(guildId, playerId, chunk);
    }

    /**
     * Unclaims all chunks for a guild.
     *
     * @param guildId the guild ID
     */
    public void unclaimAll(UUID guildId) {
        // Use the guild owner to unclaim all
        Guild guild = guildService.getGuildById(guildId);
        if (guild != null) {
            guildService.unclaimAllChunks(guildId, guild.getOwnerId());
        }
    }

    /**
     * Gets the guild that owns a chunk.
     *
     * @param chunk the chunk
     * @return the guild owner, or null if unclaimed
     */
    public Guild getChunkOwner(ChunkKey chunk) {
        return guildService.getChunkOwner(chunk);
    }

    /**
     * Gets all chunks claimed by a guild.
     *
     * @param guildId the guild ID
     * @return list of claimed chunks
     */
    public List<ChunkKey> getGuildChunks(UUID guildId) {
        return guildService.getGuildChunks(guildId);
    }

    /**
     * Gets the claim log entries for a guild.
     *
     * @param guildId the guild ID
     * @param limit the maximum number of entries
     * @return list of claim log entries
     */
    public List<ChunkClaimLog> getGuildClaimLogs(UUID guildId, int limit) {
        return guildService.getGuildClaimLogs(guildId, limit);
    }
}
