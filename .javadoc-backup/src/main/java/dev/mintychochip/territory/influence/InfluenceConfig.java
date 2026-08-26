package dev.mintychochip.territory.influence;

import java.util.Objects;

/** Immutable influence tuning values (spec §4, §12). Pure domain. */
public record InfluenceConfig(
        boolean enabled,
        double cap,
        double pvpKill,
        double pveKill,
        double blockBreak,
        double blockPlace,
        double craft,
        double defenderMultiplier,
        long declareCountdownHours,
        long postFlipCooldownDays,
        long flushSeconds
) {

    public InfluenceConfig {
        if (cap <= 0) {
            throw new IllegalArgumentException("influence cap must be positive");
        }
        if (pvpKill < 0 || pveKill < 0 || blockBreak < 0 || blockPlace < 0 || craft < 0) {
            throw new IllegalArgumentException("influence source values must be non-negative");
        }
        if (defenderMultiplier < 0) {
            throw new IllegalArgumentException("defender multiplier must be non-negative");
        }
        if (declareCountdownHours < 0) {
            throw new IllegalArgumentException("declare countdown hours must be non-negative");
        }
        if (postFlipCooldownDays < 0) {
            throw new IllegalArgumentException("post-flip cooldown days must be non-negative");
        }
        if (flushSeconds <= 0) {
            throw new IllegalArgumentException("flush seconds must be positive");
        }
    }

    public static InfluenceConfig defaults() {
        return new InfluenceConfig(
                true, 100.0, 10.0, 0.5, 0.1, 0.1, 0.2,
                1.0, 24, 7, 60);
    }

    /** Per-source accrual value for an attacker event. */
    public double valueOf(InfluenceSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source) {
            case PVP_KILL -> pvpKill;
            case PVE_KILL -> pveKill;
            case BLOCK_BREAK -> blockBreak;
            case BLOCK_PLACE -> blockPlace;
            case CRAFT -> craft;
        };
    }

    /** Accrual value a defender event removes from every attacker bar. */
    public double defenderValueOf(InfluenceSource source) {
        return valueOf(source) * defenderMultiplier;
    }

    public long declareCountdownEpochMs() {
        return declareCountdownHours * 3_600_000L;
    }

    public long postFlipCooldownEpochMs() {
        return postFlipCooldownDays * 86_400_000L;
    }
}
