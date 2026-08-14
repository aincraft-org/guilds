package com.azoth.territory.invasion;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvasionConfigLoaderTest {
    @Test
    void defaultsAreLoaded() {
        var cfg = InvasionConfigLoader.fromBukkit(YamlConfiguration.loadConfiguration(new StringReader("")));
        assertEquals(true, cfg.enabled());
        assertEquals(500, cfg.config().blockBudget());
        assertEquals(24, cfg.spawnRadius());
        assertEquals(24, cfg.spawnAttempts());
        assertEquals(96, cfg.nearbyRadius());
        assertEquals(100, cfg.waveDelayTicks());
        assertEquals(3, cfg.config().waves().size());
    }
    @Test
    void enabledOnlyRetainsAllDefaults() {
        var cfg = load("invasions:\n  enabled: false\n");
        assertEquals(false, cfg.enabled());
        assertEquals(500, cfg.config().blockBudget());
        assertEquals(4, cfg.materials().size());
        assertEquals(3, cfg.config().waves().size());
        assertEquals(24, cfg.spawnAttempts());
    }
    @Test
    void bundledYamlUsesListWaveSchema() {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/config.yml"));
        assertEquals(3, InvasionConfigLoader.fromBukkit(cfg).config().waves().size());
    }

    @Test
    void malformedWaveCountsAreRejectedBeforeConversion() {
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml("1.5")));
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml("foo")));
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml(".nan")));
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml(".inf")));
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml("2147483648")));
        assertThrows(IllegalArgumentException.class, () -> load(wavesYaml("-2147483649")));
    }

    private static String wavesYaml(String count) {
        return """
                invasions:
                  waves:
                    - entities: [ZOMBIE]
                      counts: [%s]
                    - entities: [ZOMBIE]
                      counts: [1]
                    - entities: [ZOMBIE]
                      counts: [1]
                """.formatted(count);
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> load("invasions:\n  damage:\n    block-budget: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> load("invasions:\n  waves: []\n"));
        assertThrows(IllegalArgumentException.class, () -> load("invasions:\n  spawn-radius: -1\n"));
        assertThrows(IllegalArgumentException.class, () -> load("invasions:\n  spawn-attempts: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> load("invasions:\n  wave-delay-ticks: -1\n"));
    }

    private static InvasionConfigLoader.LoadedConfig load(String yaml) {
        return InvasionConfigLoader.fromBukkit(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }
}
