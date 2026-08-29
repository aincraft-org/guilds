package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastTravelCostCalculatorTest {
    @Test
    void usesCanonicalCeilAndModeMultipliers() {
        FastTravelCostCalculator calculator = new FastTravelCostCalculator(config());

        assertEquals(2L, calculator.calculate(FastTravelMode.WAYSTONE, 100.0));
        assertEquals(3L, calculator.calculate(FastTravelMode.CRYSTAL, 100.0));
        assertEquals(4L, calculator.calculate(FastTravelMode.BOAT, 100.0));
        assertEquals(4L, calculator.calculate(FastTravelMode.AIRSHIP, 100.0));
        assertEquals(2L, calculator.calculate(FastTravelMode.WAYSTONE, 100.0001));
        assertEquals(1L, calculator.calculate(FastTravelMode.WAYSTONE, 0.0));
    }

    @Test
    void rejectsInvalidAndOverflowingDistance() {
        FastTravelCostCalculator calculator = new FastTravelCostCalculator(config());

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(FastTravelMode.WAYSTONE, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(FastTravelMode.WAYSTONE, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(FastTravelMode.WAYSTONE, Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class,
                () -> calculator.calculate(FastTravelMode.WAYSTONE, Double.MAX_VALUE));
    }

    private static TravelCurrencyConfig config() {
        EnumMap<FastTravelMode, Double> multipliers = new EnumMap<>(FastTravelMode.class);
        multipliers.put(FastTravelMode.WAYSTONE, 1.0);
        multipliers.put(FastTravelMode.CRYSTAL, 2.0);
        multipliers.put(FastTravelMode.BOAT, 2.5);
        multipliers.put(FastTravelMode.AIRSHIP, 3.0);
        return new TravelCurrencyConfig(1L, 1000L, 1L, 100.0, multipliers,
                30_000L, Map.of(
                        TravelCurrencyRewardSource.QUEST_COMPLETION, 20L,
                        TravelCurrencyRewardSource.EXPLORATION_MILESTONE, 10L,
                        TravelCurrencyRewardSource.GUILD_ACTIVITY, 5L));
    }
}
