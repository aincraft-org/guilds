package com.azoth.territory.influence;

/**
 * One attacking guild's influence bar on a territory.
 *
 * @param guildId attacking guild identifier
 * @param value influence value
 */
public record InfluenceBar(String guildId, double value) {
    /** Validates the influence bar components. */
    public InfluenceBar {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
