package org.aincraft.guilds.config;

import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Objects;

/** Reads and validates the global {@code travel-currency} configuration block. */
public final class TravelCurrencyConfigLoader {
    private TravelCurrencyConfigLoader() {
    }

    public static TravelCurrencyConfig fromBukkit(FileConfiguration config) {
        return from(config);
    }

    public static TravelCurrencyConfig from(ConfigurationSection config) {
        TravelCurrencyConfig defaults = TravelCurrencyConfig.defaults();
        ConfigurationSection section = resolveSection(config);
        if (section == null) {
            return defaults;
        }

        long starter = longValue(section, defaults.starterBalance(), "starter", "starter-balance",
                "starter-grant", "starter_grant");
        long maximum = longValue(section, defaults.maximumBalance(), "maximum", "maximum-balance",
                "max-balance", "max_balance", "cap");
        long baseCost = longValue(section, defaults.baseCost(), "base-cost", "base_cost");
        double divisor = doubleValue(section, defaults.distanceDivisor(),
                "distance-divisor", "distance_divisor");
        long reservationDuration = longValue(section, defaults.reservationDurationMillis(),
                "reservation-duration-millis", "reservation_duration_millis",
                "reservation-duration-ms", "reservation_duration_ms", "reservation-duration");

        EnumMap<FastTravelMode, Double> multipliers = new EnumMap<>(FastTravelMode.class);
        for (FastTravelMode mode : FastTravelMode.values()) {
            String enumName = mode.name();
            String lowerName = enumName.toLowerCase(Locale.ROOT).replace('_', '-');
            String snakeName = lowerName.replace('-', '_');
            String path = firstPath(section, "mode-multipliers." + enumName,
                    "mode-multipliers." + lowerName, "mode-multipliers." + snakeName,
                    "mode_multipliers." + enumName, "multipliers." + enumName,
                    "multipliers." + lowerName, "multipliers." + snakeName);
            if (path != null) {
                multipliers.put(mode, doubleValue(section, 1.0, path));
            } else if (mode != FastTravelMode.LOCAL_TERMINAL) {
                multipliers.put(mode, defaults.modeMultiplier(mode));
            }
        }

        EnumMap<TravelCurrencyRewardSource, Long> rewards = new EnumMap<>(TravelCurrencyRewardSource.class);
        for (TravelCurrencyRewardSource source : TravelCurrencyRewardSource.values()) {
            String enumName = source.name();
            String lowerName = enumName.toLowerCase(Locale.ROOT).replace('_', '-');
            String snakeName = lowerName.replace('-', '_');
            String path = firstPath(section, "rewards." + enumName,
                    "rewards." + lowerName, "rewards." + snakeName,
                    "reward-amounts." + enumName, "reward_amounts." + enumName);
            if (path != null) {
                rewards.put(source, longValue(section, 0L, path));
            } else {
                rewards.put(source, defaults.rewardAmount(source));
            }
        }

        return new TravelCurrencyConfig(starter, maximum, baseCost, divisor, multipliers,
                reservationDuration, rewards);
    }

    private static ConfigurationSection resolveSection(ConfigurationSection config) {
        if (config == null) {
            return null;
        }
        for (String path : new String[] {
                "travel-currency", "travel_currency", "travelCurrency", "travel.currency"}) {
            ConfigurationSection section = config.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
            if (config.isSet(path)) {
                throw new IllegalArgumentException(path + " must be a configuration section");
            }
        }
        return config;
    }

    private static String firstPath(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.isSet(path)) {
                return path;
            }
        }
        return null;
    }

    private static long longValue(ConfigurationSection section, long defaultValue, String... paths) {
        String path = firstPath(section, paths);
        if (path == null) {
            return defaultValue;
        }
        Object raw = section.get(path);
        try {
            BigDecimal decimal = raw instanceof Number number
                    ? new BigDecimal(number.toString())
                    : new BigDecimal(Objects.toString(raw, "").trim());
            return decimal.longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("invalid integer at " + path, e);
        }
    }

    private static double doubleValue(ConfigurationSection section, double defaultValue, String... paths) {
        String path = firstPath(section, paths);
        if (path == null) {
            return defaultValue;
        }
        Object raw = section.get(path);
        final double value;
        try {
            value = raw instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(Objects.toString(raw, "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid decimal at " + path, e);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("invalid decimal at " + path);
        }
        return value;
    }
}
