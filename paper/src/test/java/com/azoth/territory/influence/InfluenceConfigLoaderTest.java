package com.azoth.territory.influence;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceConfigLoaderTest {

    private static InfluenceConfig fromYaml(String yaml) {
        return InfluenceConfigLoader.fromBukkit(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
    }

    @Test
    void defaults_whenBlockMissing() {
        InfluenceConfig cfg = fromYaml("");
        assertEquals(InfluenceConfig.defaults(), cfg);
    }

    @Test
    void readsExplicitValues() {
        InfluenceConfig cfg = fromYaml("""
                influence:
                  enabled: false
                  cap: 50.0
                  values:
                    pvp-kill: 5.0
                    pve-kill: 0.25
                    block-break: 0.05
                    block-place: 0.05
                    craft: 0.1
                  defender-multiplier: 2.0
                  declare-countdown-hours: 12
                  post-flip-cooldown-days: 3
                  flush-seconds: 30
                """);
        assertFalse(cfg.enabled());
        assertEquals(50.0, cfg.cap(), 0.001);
        assertEquals(5.0, cfg.pvpKill(), 0.001);
        assertEquals(0.25, cfg.pveKill(), 0.001);
        assertEquals(0.05, cfg.blockBreak(), 0.001);
        assertEquals(0.05, cfg.blockPlace(), 0.001);
        assertEquals(0.1, cfg.craft(), 0.001);
        assertEquals(2.0, cfg.defenderMultiplier(), 0.001);
        assertEquals(12, cfg.declareCountdownHours());
        assertEquals(3, cfg.postFlipCooldownDays());
        assertEquals(30, cfg.flushSeconds());
    }

    @Test
    void partialBlock_usesDefaultsForMissingKeys() {
        InfluenceConfig cfg = fromYaml("influence:\n  cap: 75.0\n");
        assertEquals(75.0, cfg.cap(), 0.001);
        assertEquals(InfluenceConfig.defaults().pvpKill(), cfg.pvpKill(), 0.001);
        assertTrue(cfg.enabled());
    }

    @Test
    void invalidValues_fallBackToDefaults() {
        InfluenceConfig cfg = fromYaml("influence:\n  cap: -5.0\n");
        assertEquals(InfluenceConfig.defaults(), cfg);
    }
}
