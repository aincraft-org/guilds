package com.azoth.territory.standing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingConfigTest {

    @TempDir
    Path tempDir;

    private static final String VALID = """
            {
              "version": 1,
              "cap": 500.0,
              "sources": {
                "pvp-kill": 10.0,
                "pve-kill": 0.5,
                "block-break": 0.15
              },
              "tiers": [
                { "level": 1, "threshold": 0,     "harvest_multiplier": 1.0, "influence_multiplier": 1.0 },
                { "level": 2, "threshold": 100,   "harvest_multiplier": 1.2, "influence_multiplier": 1.1 },
                { "level": 3, "threshold": 300,   "harvest_multiplier": 1.5, "influence_multiplier": 1.25 }
              ]
            }
            """;

    @Test
    void defaults_matchSpecValues() {
        StandingConfig d = StandingConfig.defaults();
        assertEquals(500.0, d.cap(), 0.001);
        assertEquals(10.0, d.valueOf(StandingSource.PVP_KILL), 0.001);
        assertEquals(0.5, d.valueOf(StandingSource.PVE_KILL), 0.001);
        assertEquals(0.15, d.valueOf(StandingSource.BLOCK_BREAK), 0.001);
        assertEquals(3, d.tiers().size());
        assertEquals(1.0, d.tiers().get(0).harvestMultiplier(), 0.001);
        assertEquals(1.25, d.tiers().get(2).influenceMultiplier(), 0.001);
    }

    @Test
    void validJson_parses() throws Exception {
        Path file = tempDir.resolve("bonuses.json");
        Files.writeString(file, VALID);
        Optional<StandingConfig> loaded = StandingConfigLoader.load(file);
        assertTrue(loaded.isPresent());
        assertEquals(500.0, loaded.get().cap(), 0.001);
        assertEquals(3, loaded.get().tiers().size());
    }

    @Test
    void missingFile_returnsEmpty() {
        assertTrue(StandingConfigLoader.load(tempDir.resolve("nope.json")).isEmpty());
    }

    @Test
    void invalidJson_returnsEmpty() throws Exception {
        Path file = tempDir.resolve("bonuses.json");
        Files.writeString(file, "{ not json");
        assertTrue(StandingConfigLoader.load(file).isEmpty());
    }

    @Test
    void validation_rejectsBadCapOrTiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(0, 10, 0.5, 0.15, List.of(new StandingTier(1, 0, 1.0, 1.0))));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15,
                        List.of(new StandingTier(1, 50, 1.0, 1.0))));
        assertThrows(IllegalArgumentException.class,
                () -> new StandingConfig(500, 10, 0.5, 0.15,
                        List.of(new StandingTier(1, 0, 1.0, 1.0),
                                new StandingTier(2, 0, 1.2, 1.1))));
    }

    @Test
    void highestTierFor_selectsSaturatingThreshold() {
        StandingConfig d = StandingConfig.defaults();
        assertEquals(1, d.highestTierFor(99.9).orElseThrow().level());
        assertEquals(2, d.highestTierFor(100.0).orElseThrow().level());
        assertEquals(3, d.highestTierFor(300.5).orElseThrow().level());
        assertEquals(3, d.highestTierFor(500.0).orElseThrow().level());
    }
}
