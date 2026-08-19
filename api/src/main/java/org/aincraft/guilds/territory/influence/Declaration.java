package org.aincraft.guilds.territory.influence;

/** An active takeover declaration (race is locked while present). */
public record Declaration(String guildId, long declaredAtEpochMs, long flipAtEpochMs) {
    public Declaration {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
    }
}
