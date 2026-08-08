package com.azoth.territory.upkeep;

/** Validated coefficients and periods for recurring territory upkeep. */
public record UpkeepConfig(
        double baseAmount,
        double chunkAmount,
        double facilityAmount,
        double developmentLevelAmount,
        long intervalEpochMs,
        long graceEpochMs
) {
    private static final long DAY_MS = 24L * 60L * 60L * 1_000L;

    public UpkeepConfig {
        requireAmount(baseAmount, "baseAmount");
        requireAmount(chunkAmount, "chunkAmount");
        requireAmount(facilityAmount, "facilityAmount");
        requireAmount(developmentLevelAmount, "developmentLevelAmount");
        if (intervalEpochMs <= 0L) {
            throw new IllegalArgumentException("intervalEpochMs must be positive");
        }
        if (graceEpochMs <= 0L) {
            throw new IllegalArgumentException("graceEpochMs must be positive");
        }
    }

    public static UpkeepConfig defaults() {
        return new UpkeepConfig(100.0, 0.5, 10.0, 25.0, 7L * DAY_MS, 2L * DAY_MS);
    }

    private static void requireAmount(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
