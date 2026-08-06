package com.azoth.territory.influence;

/** One attacking guild's influence bar on a territory. */
public record InfluenceBar(String guildId, double value) {
    public InfluenceBar {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
