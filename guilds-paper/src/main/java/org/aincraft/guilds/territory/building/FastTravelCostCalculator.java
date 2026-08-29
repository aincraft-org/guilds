package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.territory.model.FastTravelMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Computes the one canonical currency cost for every fast-travel mode. */
public final class FastTravelCostCalculator {
    private final TravelCurrencyConfig config;

    public FastTravelCostCalculator(TravelCurrencyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public long calculate(FastTravelMode mode, double distance) {
        Objects.requireNonNull(mode, "mode");
        requireFiniteNonNegative(distance);
        double raw = config.baseCost()
                + config.modeMultiplier(mode) * distance / config.distanceDivisor();
        if (!Double.isFinite(raw)) {
            throw new ArithmeticException("travel cost overflows long range");
        }
        long rounded = checkedCeilToLong(raw);
        return Math.max(0L, rounded);
    }

    public static void requireFiniteNonNegative(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
    }

    private static long checkedCeilToLong(double value) {
        try {
            return BigDecimal.valueOf(value).setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ArithmeticException("travel cost overflows long range");
        }
    }
}
