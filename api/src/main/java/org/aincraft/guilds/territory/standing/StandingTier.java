package org.aincraft.guilds.territory.standing;

/** One development tier: standing threshold + harvest/influence multipliers. */
public record StandingTier(
        int level,
        double threshold,
        double harvestMultiplier,
        double influenceMultiplier
) {
    public StandingTier {
        if (level < 1) {
            throw new IllegalArgumentException("tier level must be >= 1");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("tier threshold must be >= 0");
        }
        if (harvestMultiplier < 1.0 || influenceMultiplier < 1.0) {
            throw new IllegalArgumentException("tier multipliers must be >= 1.0");
        }
    }
}
