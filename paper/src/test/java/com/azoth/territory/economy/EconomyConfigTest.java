package com.azoth.territory.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyConfigTest {

    private static EconomyConfig fromYaml(String yaml) {
        return EconomyConfig.fromBukkit(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
    }

    @Test
    void defaultIsVault() {
        assertEquals(EconomyConfig.Mode.VAULT, fromYaml("").mode());
    }

    @Test
    void readsExplicitMode() {
        assertEquals(EconomyConfig.Mode.SIMULATION,
                fromYaml("economy:\n  mode: SIMULATION").mode());
        assertEquals(EconomyConfig.Mode.VAULT,
                fromYaml("economy:\n  mode: VAULT").mode());
    }

    @Test
    void unknownModeFallsBackToVault() {
        assertEquals(EconomyConfig.Mode.VAULT,
                fromYaml("economy:\n  mode: COINS").mode());
    }
}
