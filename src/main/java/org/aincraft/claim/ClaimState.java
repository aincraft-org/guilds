package org.aincraft.claim;

import java.util.Objects;
import java.util.UUID;
import org.aincraft.Guild;

/**
 * Immutable state representing current guild claim and subregion context for a player.
 * Used to efficiently detect ownership and type transitions.
 */
public record ClaimState(
    UUID guildId,             // null = wilderness
    String subregionType,     // null = not in subregion
    String displayName,       // guild name or "Wilderness"
    String guildColor         // guild color in hex format (#RRGGBB) or null
) {
    public ClaimState {
        Objects.requireNonNull(displayName, "Display name cannot be null");
    }

    /**
     * Creates a wilderness state (not owned by any guild).
     */
    public static ClaimState wilderness() {
        return new ClaimState(null, null, "Wilderness", null);
    }

    /**
     * Creates a claim state from a guild.
     *
     * @param guild the guild that owns the chunk
     * @param subregionType the type of subregion, or null if not in a typed subregion
     */
    public static ClaimState ofGuild(Guild guild, String subregionType) {
        Objects.requireNonNull(guild, "Guild cannot be null");
        return new ClaimState(guild.getId(), subregionType, guild.getName(), guild.getColor());
    }

    /**
     * Checks if ownership changed (guild changed or wilderness transition).
     */
    public boolean ownershipChangedFrom(ClaimState previous) {
        if (previous == null) return true;
        return !Objects.equals(this.guildId, previous.guildId);
    }

    /**
     * Checks if subregion type changed.
     */
    public boolean typeChangedFrom(ClaimState previous) {
        if (previous == null) return this.subregionType != null;
        return !Objects.equals(this.subregionType, previous.subregionType);
    }

    /**
     * Checks if any property changed (ownership OR type).
     */
    public boolean changedFrom(ClaimState previous) {
        if (previous == null) return true;
        return ownershipChangedFrom(previous) || typeChangedFrom(previous);
    }
}
