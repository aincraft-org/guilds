package org.aincraft.service;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.ClaimResult;
import org.aincraft.Guild;
import org.aincraft.claim.ChunkClaimLog;
import org.aincraft.claim.ChunkClaimLogRepository;
import org.aincraft.config.GuildsConfig;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.storage.GuildRepository;

/**
 * Service for managing chunk claims.
 * Single Responsibility: Chunk claiming and unclaiming operations.
 */
public class ChunkClaimService {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int HEAD_BLOCK_OFFSET = 1;

    private final ChunkClaimRepository chunkClaimRepository;
    private final ChunkClaimLogRepository claimLogRepository;
    private final GuildRepository guildRepository;
    private final GuildsConfig config;

    @Inject
    public ChunkClaimService(ChunkClaimRepository chunkClaimRepository,
                             ChunkClaimLogRepository claimLogRepository,
                             GuildRepository guildRepository,
                             GuildsConfig config) {
        this.chunkClaimRepository = Objects.requireNonNull(chunkClaimRepository, "ChunkClaimRepository cannot be null");
        this.claimLogRepository = Objects.requireNonNull(claimLogRepository, "ClaimLogRepository cannot be null");
        this.guildRepository = Objects.requireNonNull(guildRepository, "GuildRepository cannot be null");
        this.config = Objects.requireNonNull(config, "GuildsConfig cannot be null");
    }

    /**
     * Claims a chunk for a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player claiming the chunk
     * @param chunk the chunk to claim
     * @param hasClaimPermission whether the player has CLAIM permission
     * @return ClaimResult indicating success or failure
     */
    public ClaimResult claimChunk(UUID guildId, UUID playerId, ChunkKey chunk, boolean hasClaimPermission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        if (!hasClaimPermission) {
            return ClaimResult.noPermission();
        }

        // Check if already claimed by this guild
        Guild chunkOwner = getChunkOwner(chunk);
        if (chunkOwner != null && chunkOwner.getId().equals(guildId)) {
            return ClaimResult.alreadyOwned();
        }

        // Check if already claimed by another guild
        if (chunkOwner != null) {
            return ClaimResult.alreadyClaimed(chunkOwner.getName());
        }

        // Check buffer distance to other guilds
        ClaimResult bufferCheck = checkBufferDistance(chunk, guildId);
        if (!bufferCheck.isSuccess()) {
            return bufferCheck;
        }

        // Validate chunk is adjacent to existing claims or is first claim
        List<ChunkKey> guildChunks = chunkClaimRepository.getGuildChunks(guildId);
        if (!guildChunks.isEmpty() && !isAdjacentToGuild(chunk, guildChunks)) {
            return ClaimResult.notAdjacent();
        }

        // Check if guild has reached claim limit
        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            if (guildChunks.size() >= guild.getMaxChunks()) {
                return ClaimResult.limitExceeded(guild.getMaxChunks());
            }
        }

        boolean claimed = chunkClaimRepository.claim(chunk, guildId, playerId);
        if (!claimed) {
            return ClaimResult.failure("Failed to claim chunk");
        }

        // Log the claim action
        claimLogRepository.log(new ChunkClaimLog(guildId, chunk, playerId, ChunkClaimLog.ActionType.CLAIM));

        // Auto-set homeblock if this is first claim
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            if (!guild.hasHomeblock()) {
                guild.setHomeblock(chunk);
                guildRepository.save(guild);
            }
        }

        return ClaimResult.success();
    }

    /**
     * Unclaims a chunk from a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player unclaiming
     * @param chunk the chunk to unclaim
     * @param hasUnclaimPermission whether the player has UNCLAIM permission
     * @return true if unclaimed successfully
     */
    public boolean unclaimChunk(UUID guildId, UUID playerId, ChunkKey chunk, boolean hasUnclaimPermission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        if (!hasUnclaimPermission) {
            return false;
        }

        // Prevent unclaiming homeblock
        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isPresent()) {
            Guild guild = guildOpt.get();
            if (guild.hasHomeblock() && chunk.equals(guild.getHomeblock())) {
                return false;
            }
        }

        boolean unclaimed = chunkClaimRepository.unclaim(chunk, guildId);
        if (unclaimed) {
            claimLogRepository.log(new ChunkClaimLog(guildId, chunk, playerId, ChunkClaimLog.ActionType.UNCLAIM));
        }

        return unclaimed;
    }

    /**
     * Unclaims all chunks owned by a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player unclaiming
     * @param hasUnclaimPermission whether the player has UNCLAIM permission
     * @return true if unclaimed successfully
     */
    public boolean unclaimAllChunks(UUID guildId, UUID playerId, boolean hasUnclaimPermission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (!hasUnclaimPermission) {
            return false;
        }

        List<ChunkKey> chunks = chunkClaimRepository.getGuildChunks(guildId);
        chunkClaimRepository.unclaimAll(guildId);

        for (ChunkKey chunk : chunks) {
            claimLogRepository.log(new ChunkClaimLog(guildId, chunk, playerId, ChunkClaimLog.ActionType.UNCLAIM));
        }

        return true;
    }

    /**
     * Gets the guild that owns a chunk.
     *
     * @param chunk the chunk to check
     * @return the guild if claimed, null otherwise
     */
    public Guild getChunkOwner(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        return chunkClaimRepository.getOwner(chunk)
                .flatMap(guildRepository::findById)
                .orElse(null);
    }

    /**
     * Gets all chunks claimed by a guild.
     *
     * @param guildId the guild ID
     * @return list of chunk keys
     */
    public List<ChunkKey> getGuildChunks(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return chunkClaimRepository.getGuildChunks(guildId);
    }

    /**
     * Gets claim log entries for a guild.
     *
     * @param guildId the guild ID
     * @param limit maximum number of entries to return
     * @return list of claim log entries, newest first
     */
    public List<ChunkClaimLog> getGuildClaimLogs(UUID guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return claimLogRepository.findByGuildId(guildId, limit);
    }

    /**
     * Gets the number of chunks claimed by a guild.
     *
     * @param guildId the guild ID
     * @return the chunk count
     */
    public int getGuildChunkCount(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return chunkClaimRepository.getChunkCount(guildId);
    }

    private boolean isAdjacentToGuild(ChunkKey chunk, List<ChunkKey> guildChunks) {
        for (ChunkKey existing : guildChunks) {
            if (!chunk.world().equals(existing.world())) {
                continue;
            }

            int dx = Math.abs(chunk.x() - existing.x());
            int dz = Math.abs(chunk.z() - existing.z());

            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return true;
            }
        }
        return false;
    }

    private ClaimResult checkBufferDistance(ChunkKey chunk, UUID guildId) {
        int bufferDistance = config.getClaimBufferDistance();

        if (bufferDistance == 0) {
            return ClaimResult.success();
        }

        List<Guild> allGuilds = guildRepository.findAll();

        for (Guild otherGuild : allGuilds) {
            if (otherGuild.getId().equals(guildId)) {
                continue;
            }

            List<ChunkKey> otherChunks = chunkClaimRepository.getGuildChunks(otherGuild.getId());

            for (ChunkKey otherChunk : otherChunks) {
                if (!chunk.world().equals(otherChunk.world())) {
                    continue;
                }

                int dx = Math.abs(chunk.x() - otherChunk.x());
                int dz = Math.abs(chunk.z() - otherChunk.z());
                int manhattanDistance = dx + dz;

                if (manhattanDistance < bufferDistance) {
                    return ClaimResult.tooCloseToGuild(otherGuild.getName(), bufferDistance, manhattanDistance);
                }
            }
        }

        return ClaimResult.success();
    }
}
