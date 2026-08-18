package dev.mintychochip.guilds.projects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for guild skill points. */
class GuildSkillPointsTest {

    /** Performs the earned unspent and spent track level changes operation. */
    @Test
    void earnedUnspentAndSpentTrackLevelChanges() {
        assertEquals(3, GuildSkillPoints.totalEarned(3));
        assertEquals(0, GuildSkillPoints.totalEarned(-2));
        assertEquals(4, GuildSkillPoints.unspentAfterLevelChange(2, 3, 5));
        assertEquals(1, GuildSkillPoints.spent(4, 3));
    }
}
