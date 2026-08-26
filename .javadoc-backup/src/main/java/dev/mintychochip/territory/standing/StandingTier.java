package dev.mintychochip.territory.standing;

/**
 * One development tier: standing threshold + harvest/influence multipliers.
 *
 * @param level tier number, starting at one
 * @param threshold minimum standing required for this tier
 * @param harvestMultiplier harvest multiplier granted by this tier
 * @param influenceMultiplier influence multiplier granted by this tier
 */
public record StandingTier(
        int level,
        double threshold,
        double harvestMultiplier,
        double influenceMultiplier
) {
    /** Validates the tier level, threshold, and multipliers. */
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
