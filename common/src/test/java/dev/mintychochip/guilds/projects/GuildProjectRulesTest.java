package dev.mintychochip.guilds.projects;

import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.services.GuildProjectService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for guild project rules. */
class GuildProjectRulesTest {

    /** Performs the evaluate start accepts affordable root and rejects blocked states operation. */
    @Test
    void evaluateStartAcceptsAffordableRootAndRejectsBlockedStates() {
        TechTreeNode root = new TechTreeNode("better_storage");
        root.setCost(1);
        root.setPrerequisites(List.of());

        assertEquals(GuildProjectService.StartStatus.STARTED,
                GuildProjectRules.evaluateStart(root, null, Set.of(), 2));
        assertEquals(1, GuildProjectRules.unspentAfterStart(2, 1));

        TechTreeNode child = new TechTreeNode("fast_travel");
        child.setCost(1);
        child.setPrerequisites(List.of("better_storage"));
        assertEquals(GuildProjectService.StartStatus.UNMET_REQUIREMENTS,
                GuildProjectRules.evaluateStart(child, null, Set.of(), 2));
        assertEquals(GuildProjectService.StartStatus.ALREADY_ACTIVE,
                GuildProjectRules.evaluateStart(root, "better_storage", Set.of(), 2));
        assertEquals(GuildProjectService.StartStatus.INSUFFICIENT_POINTS,
                GuildProjectRules.evaluateStart(root, null, Set.of(), 0));
        assertTrue(GuildProjectRules.canClear("better_storage"));
        assertFalse(GuildProjectRules.canClear(null));
    }
}
