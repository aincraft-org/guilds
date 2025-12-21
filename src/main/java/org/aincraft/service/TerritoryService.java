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
     * @param playerId the player performing the unclaim
     * @return true if unclaimed successfully, false if no UNCLAIM_ALL permission
     */
    public boolean unclaimAll(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        return guildService.unclaimAllChunks(guildId, playerId);
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

    /**
     * Validates that a chunk meets the buffer distance requirements from other guilds.
     * Does NOT check if the chunk is already claimed or other possession checks.
     * Used for pre-validation before guild creation.
     *
     * @param chunk the chunk to validate
     * @param guildId the guild ID (used to exclude from buffer check)
     * @return ClaimResult.success() if buffer distance is valid, or ClaimResult.tooCloseToGuild() if not
     */
    public ClaimResult validateBufferDistance(ChunkKey chunk, UUID guildId) {
        return guildService.validateBufferDistance(chunk, guildId);
    }
}
