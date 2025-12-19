package org.aincraft.progression;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a progression event log entry.
 * Records XP gains, level ups, and admin modifications for audit purposes.
 */
public record ProgressionLog(
        long id,
        UUID guildId,
        UUID playerId,
        ActionType action,
        long amount,
        String details,
        long timestamp
) {

    /**
     * Creates a new ProgressionLog with auto-generated timestamp and id=0.
     * The database will assign the actual id.
     */
    public ProgressionLog(UUID guildId, UUID playerId, ActionType action, long amount, String details) {
        this(0, guildId, playerId, action, amount, details, System.currentTimeMillis());
    }

    public ProgressionLog {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");
    }

    /**
     * Type of progression action.
     */
    public enum ActionType {
        XP_GAIN,           // Player earned XP from gameplay
        LEVEL_UP,          // Guild leveled up
        ADMIN_SET_LEVEL,   // Admin set guild level
        ADMIN_ADD_XP,      // Admin added XP
        ADMIN_SET_XP,      // Admin set XP
        ADMIN_RESET_LEVEL, // Admin reset level to 1
        ADMIN_RESET_XP     // Admin reset XP to 0
    }
}
