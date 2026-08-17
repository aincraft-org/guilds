package dev.mintychochip.territory.standing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable standing tuning values + tier table (spec §5). Pure domain. */
public record StandingConfig(
        double cap,
        double pvpKill,
        double pveKill,
        double blockBreak,
        List<StandingTier> tiers
) {

    public StandingConfig {
        if (cap <= 0) {
            throw new IllegalArgumentException("standing cap must be positive");
        }
        if (pvpKill < 0 || pveKill < 0 || blockBreak < 0) {
            throw new IllegalArgumentException("standing source values must be non-negative");
        }
        Objects.requireNonNull(tiers, "tiers");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one standing tier is required");
        }
        if (tiers.get(0).threshold() != 0.0) {
            throw new IllegalArgumentException("first standing tier must start at threshold 0");
        }
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).threshold() <= tiers.get(i - 1).threshold()) {
                throw new IllegalArgumentException("standing tier thresholds must be ascending");
            }
        }
    }

    public static StandingConfig defaults() {
        return new StandingConfig(
                500.0, 10.0, 0.5, 0.15,
                List.of(
                        new StandingTier(1, 0, 1.0, 1.0),
                        new StandingTier(2, 100, 1.2, 1.1),
                        new StandingTier(3, 300, 1.5, 1.25)
                ));
    }

    /** Per-source standing value for an eligible actor event. */
    public double valueOf(StandingSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source) {
            case PVP_KILL -> pvpKill;
            case PVE_KILL -> pveKill;
            case BLOCK_BREAK -> blockBreak;
        };
    }

    /** Highest tier whose threshold is satisfied by {@code standing}. */
    public Optional<StandingTier> highestTierFor(double standing) {
        StandingTier best = null;
        for (StandingTier tier : tiers) {
            if (standing >= tier.threshold()) {
                best = tier;
            }
        }
        return Optional.ofNullable(best);
    }
}
