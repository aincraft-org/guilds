package org.aincraft.claim;

import org.aincraft.ChunkKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a chunk claim or unclaim log entry.
 * Records historical claim/unclaim actions for audit purposes.
 */
public record ChunkClaimLog(
        long id,
        String guildId,
        ChunkKey chunk,
        UUID playerId,
        ActionType action,
        long timestamp
) {

    /**
     * Creates a new ChunkClaimLog with auto-generated timestamp and id=0.
     * The database will assign the actual id.
     */
    public ChunkClaimLog(String guildId, ChunkKey chunk, UUID playerId, ActionType action) {
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
