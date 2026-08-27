package dev.mintychochip.guilds.projects;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for xp upgrade gate. */
class XpUpgradeGateTest {

    /** Performs the required and contributed experience ignore material leftovers operation. */
    @Test
    void requiredAndContributedExperienceIgnoreMaterialLeftovers() {
        Map<String, Integer> costs = Map.of(
                "experience", 40,
                "diamond", 12,
                "EXPERIENCE_BOTTLE", 10
        );
        Map<String, Integer> progress = Map.of(
                "experience", 25,
                "diamond", 99
        );

        assertEquals(50, XpUpgradeGate.requiredExperience(costs));
        assertEquals(25, XpUpgradeGate.contributedExperience(progress));
        assertFalse(XpUpgradeGate.hasEnoughExperience(progress, 50));
        assertTrue(XpUpgradeGate.hasEnoughExperience(Map.of("experience", 50), 50));
        assertTrue(XpUpgradeGate.isExperienceKey("EXPERIENCE"));
        assertFalse(XpUpgradeGate.isExperienceKey("diamond"));
    }
}
