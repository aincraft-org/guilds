package dev.mintychochip.territory.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyConfigTest {

    private static EconomyConfig fromYaml(String yaml) {
        return EconomyConfig.fromBukkit(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
    }

    @Test
    void defaultIsSimulation() {
        assertEquals(EconomyConfig.Mode.SIMULATION, fromYaml("").mode());
    }

    @Test
    void readsExplicitMode() {
        assertEquals(EconomyConfig.Mode.SIMULATION,
                fromYaml("economy:\n  mode: SIMULATION").mode());
        assertEquals(EconomyConfig.Mode.MINT,
                fromYaml("economy:\n  mode: MINT").mode());
    }

    @Test
    void unknownModeFallsBackToSimulation() {
        assertEquals(EconomyConfig.Mode.SIMULATION,
                fromYaml("economy:\n  mode: COINS").mode());
    }
}
