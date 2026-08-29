package org.aincraft.guilds.config;

import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.territory.model.FastTravelMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Validated global policy for personal travel currency. */
public record TravelCurrencyConfig(
        long starterBalance,
        long maximumBalance,
        long baseCost,
        double distanceDivisor,
        Map<FastTravelMode, Double> modeMultipliers,
        long reservationDurationMillis,
        Map<TravelCurrencyRewardSource, Long> rewardAmounts
) {
    public TravelCurrencyConfig {
        if (starterBalance < 0L) {
            throw new IllegalArgumentException("starter balance cannot be negative");
        }
        if (maximumBalance < starterBalance) {
            throw new IllegalArgumentException("maximum balance must be at least starter balance");
        }
        if (baseCost < 0L) {
            throw new IllegalArgumentException("base cost cannot be negative");
        }
        if (!Double.isFinite(distanceDivisor) || distanceDivisor <= 0.0) {
            throw new IllegalArgumentException("distance divisor must be positive and finite");
        }
        if (reservationDurationMillis <= 0L) {
            throw new IllegalArgumentException("reservation duration must be positive");
        }
        modeMultipliers = copyMultipliers(modeMultipliers);
        rewardAmounts = copyRewards(rewardAmounts);
    }

    public static TravelCurrencyConfig defaults() {
        EnumMap<FastTravelMode, Double> multipliers = new EnumMap<>(FastTravelMode.class);
        multipliers.put(FastTravelMode.WAYSTONE, 1.0);
        multipliers.put(FastTravelMode.CRYSTAL, 1.0);
        multipliers.put(FastTravelMode.BOAT, 1.25);
        multipliers.put(FastTravelMode.AIRSHIP, 1.5);

        EnumMap<TravelCurrencyRewardSource, Long> rewards = new EnumMap<>(TravelCurrencyRewardSource.class);
        rewards.put(TravelCurrencyRewardSource.QUEST_COMPLETION, 20L);
        rewards.put(TravelCurrencyRewardSource.EXPLORATION_MILESTONE, 10L);
        rewards.put(TravelCurrencyRewardSource.GUILD_ACTIVITY, 5L);
        return new TravelCurrencyConfig(10L, 1_000L, 1L, 100.0, multipliers, 30_000L, rewards);
    }

    public double modeMultiplier(FastTravelMode mode) {
        Objects.requireNonNull(mode, "mode");
        // LOCAL_TERMINAL is a local endpoint and intentionally follows the
        // baseline multiplier unless an operator explicitly configures it.
        return modeMultipliers.getOrDefault(mode, 1.0);
    }

    public long rewardAmount(TravelCurrencyRewardSource source) {
        Objects.requireNonNull(source, "source");
        return rewardAmounts.getOrDefault(source, 0L);
    }

    /** Short aliases useful to callers that refer to the policy's starter/cap terms. */
    public long starter() {
        return starterBalance;
    }

    public long maximum() {
        return maximumBalance;
    }

    public long maxBalance() {
        return maximumBalance;
    }

    public long reservationDuration() {
        return reservationDurationMillis;
    }

    private static Map<FastTravelMode, Double> copyMultipliers(Map<FastTravelMode, Double> values) {
        Objects.requireNonNull(values, "modeMultipliers");
        EnumMap<FastTravelMode, Double> copy = new EnumMap<>(FastTravelMode.class);
        values.forEach((mode, multiplier) -> {
            if (mode == null || multiplier == null
                    || !Double.isFinite(multiplier) || multiplier <= 0.0) {
                throw new IllegalArgumentException("mode multipliers must be positive and finite");
            }
            copy.put(mode, multiplier);
        });
        return Map.copyOf(copy);
    }

    private static Map<TravelCurrencyRewardSource, Long> copyRewards(
            Map<TravelCurrencyRewardSource, Long> values) {
        Objects.requireNonNull(values, "rewardAmounts");
        EnumMap<TravelCurrencyRewardSource, Long> copy = new EnumMap<>(TravelCurrencyRewardSource.class);
        values.forEach((source, amount) -> {
            if (source == null || amount == null || amount < 0L) {
                throw new IllegalArgumentException("reward amounts cannot be negative");
            }
            copy.put(source, amount);
        });
        return Map.copyOf(copy);
    }
}
