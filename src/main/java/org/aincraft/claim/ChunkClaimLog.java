package org.aincraft.claim;

import java.util.Objects;
import java.util.UUID;
import org.aincraft.ChunkKey;

/**
 * Represents a chunk claim or unclaim log entry.
 * Records historical claim/unclaim actions for audit purposes.
 */
public record ChunkClaimLog(
        long id,
        UUID guildId,
        ChunkKey chunk,
        UUID playerId,
        ActionType action,
        long timestamp
) {

    /**
     * Creates a new ChunkClaimLog with auto-generated timestamp and id=0.
     * The database will assign the actual id.
     */
    public ChunkClaimLog(UUID guildId, ChunkKey chunk, UUID playerId, ActionType action) {
        this(0, guildId, chunk, playerId, action, System.currentTimeMillis());
    }

    public ChunkClaimLog {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");
    }

    /**
     * Type of claim action.
     */
    public enum ActionType {
        CLAIM,
        UNCLAIM
    }
}
