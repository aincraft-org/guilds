package org.aincraft.guilds.config;

import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TravelCurrencyConfigLoaderTest {
    @Test
    void defaultsAreLoaded() {
        TravelCurrencyConfig config = TravelCurrencyConfigLoader.fromBukkit(
                YamlConfiguration.loadConfiguration(new StringReader("")));

        assertEquals(10L, config.starterBalance());
        assertEquals(1_000L, config.maximumBalance());
        assertEquals(1L, config.baseCost());
        assertEquals(100.0, config.distanceDivisor());
        assertEquals(1.0, config.modeMultiplier(FastTravelMode.WAYSTONE));
        assertEquals(1.0, config.modeMultiplier(FastTravelMode.CRYSTAL));
        assertEquals(1.25, config.modeMultiplier(FastTravelMode.BOAT));
        assertEquals(1.5, config.modeMultiplier(FastTravelMode.AIRSHIP));
        assertEquals(30_000L, config.reservationDurationMillis());
        assertEquals(20L, config.rewardAmount(TravelCurrencyRewardSource.QUEST_COMPLETION));
        assertEquals(10L, config.rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE));
        assertEquals(5L, config.rewardAmount(TravelCurrencyRewardSource.GUILD_ACTIVITY));
    }

    @Test
    void customValuesAreLoadedAndValidated() {
        TravelCurrencyConfig config = load("""
                travel-currency:
                  starter: 4
                  maximum: 40
                  base-cost: 2
                  distance-divisor: 50.0
                  mode-multipliers:
                    WAYSTONE: 1.1
                    CRYSTAL: 1.2
                    BOAT: 1.3
                    AIRSHIP: 1.4
                  reservation-duration-millis: 5000
                  rewards:
                    QUEST_COMPLETION: 8
                    EXPLORATION_MILESTONE: 7
                    GUILD_ACTIVITY: 6
                """);
        assertEquals(4L, config.starterBalance());
        assertEquals(40L, config.maximumBalance());
        assertEquals(2L, config.baseCost());
        assertEquals(50.0, config.distanceDivisor());
        assertEquals(1.4, config.modeMultiplier(FastTravelMode.AIRSHIP));
        assertEquals(5_000L, config.reservationDurationMillis());
        assertEquals(8L, config.rewardAmount(TravelCurrencyRewardSource.QUEST_COMPLETION));
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  starter: -1\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  maximum: 9\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  base-cost: -1\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  distance-divisor: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  distance-divisor: .nan\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  reservation-duration-millis: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  rewards:\n    QUEST_COMPLETION: -1\n"));
        assertThrows(IllegalArgumentException.class, () -> load("travel-currency:\n  mode-multipliers:\n    BOAT: 0\n"));
    }

    private static TravelCurrencyConfig load(String yaml) {
        return TravelCurrencyConfigLoader.fromBukkit(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }
}
