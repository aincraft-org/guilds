package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Form-specific policy propose / vote / decree paths on shipped {@link PolicyRules}.
 */
class PolicyRulesTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void monarchy_decreePasses() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "tax-cut", "Tax Cut", "Lower tariffs", "king:arthur", NOW);
        assertEquals(PolicyStatus.PROPOSED, p.status());
        Policy passed = PolicyRules.decree(g, p, "king:arthur", true, NOW + 1);
        assertEquals(PolicyStatus.PASSED, passed.status());
        assertEquals(VoteChoice.YES, passed.voteOf("king:arthur").orElseThrow().choice());
    }

    @Test
    void monarchy_ineligibleCannotDecree() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "p1", "P", "B", "king:arthur", NOW);
        assertThrows(IllegalArgumentException.class,
                () -> PolicyRules.decree(g, p, "peasant:bob", true, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> PolicyRules.castVote(g, p, "king:arthur", VoteChoice.YES, NOW));
    }

    @Test
    void oligarchy_majorityPassAndFail() {
        Government g = Government.oligarchy(List.of("c1", "c2", "c3"));
        Policy p = PolicyRules.propose(g, "wall", "Build Wall", "…", "c1", NOW);
        p = PolicyRules.castVote(g, p, "c1", VoteChoice.YES, NOW);
        assertEquals(PolicyStatus.PROPOSED, p.status());
        p = PolicyRules.castVote(g, p, "c2", VoteChoice.YES, NOW + 1);
        // 2 of 3 yes → strict majority (> 1.5 → >1) threshold filled/2=1, yes>1
        assertEquals(PolicyStatus.PASSED, p.status());

        Policy fail = PolicyRules.propose(g, "tax", "Raise Tax", "…", "c1", NOW);
        fail = PolicyRules.castVote(g, fail, "c1", VoteChoice.NO, NOW);
        fail = PolicyRules.castVote(g, fail, "c2", VoteChoice.NO, NOW + 1);
        assertEquals(PolicyStatus.REJECTED, fail.status());
    }

    @Test
    void democracy_majorityDistinctFromMonarchy() {
        Government g = Government.democracy(3, List.of("r1", "r2", "r3"), null);
        assertEquals(GovernmentForm.DecisionStyle.MAJORITY_SEATS, g.form().decisionStyle());
        assertTrue(PolicyRules.describeDecisionPath(GovernmentForm.DEMOCRACY).contains("REPRESENTATIVE"));
        assertTrue(PolicyRules.describeDecisionPath(GovernmentForm.MONARCHY).contains("decree"));

        Policy p = PolicyRules.propose(g, "roads", "Roads", "Fund roads", "r1", NOW);
        // single yes does not pass under democracy (need majority of 3 → yes > 1)
        p = PolicyRules.castVote(g, p, "r1", VoteChoice.YES, NOW);
        assertEquals(PolicyStatus.PROPOSED, p.status());
        p = PolicyRules.castVote(g, p, "r2", VoteChoice.YES, NOW + 1);
        assertEquals(PolicyStatus.PASSED, p.status());
    }

    @Test
    void none_cannotPropose() {
        assertThrows(IllegalArgumentException.class,
                () -> PolicyRules.propose(Government.anarchy(), "x", "t", "b", "anyone", NOW));
    }

    @Test
    void ineligibleVoterRejected() {
        Government g = Government.oligarchy(List.of("c1", "c2", "c3"));
        Policy p = PolicyRules.propose(g, "p", "P", "B", "c1", NOW);
        assertThrows(IllegalArgumentException.class,
                () -> PolicyRules.castVote(g, p, "outsider", VoteChoice.YES, NOW));
    }
}
