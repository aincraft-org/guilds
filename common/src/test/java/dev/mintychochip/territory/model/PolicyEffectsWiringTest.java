package dev.mintychochip.territory.model;

import dev.mintychochip.territory.decree.DecreeEffects;
import dev.mintychochip.territory.decree.TaxEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Effects added to Policy: constructor and with-method wiring, and preservation
 * through vote and decree.
 */
class PolicyEffectsWiringTest {

    private static final long NOW = 1_700_000_000_000L;

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    @Test
    void newPolicyDefaultsToEmptyEffects() {
        Policy p = Policy.propose("p1", "Title", "Body", "proposer", NOW);
        assertTrue(p.effects().isEmpty());
    }

    @Test
    void proposeCarriesEffects() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        assertEquals(carrotTax(), p.effects());
    }

    @Test
    void votePreservesEffects() {
        Government g = Government.oligarchy(List.of("c1", "c2", "c3"));
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "c1", NOW, carrotTax());
        Policy voted = PolicyRules.castVote(g, p, "c1", VoteChoice.YES, NOW + 1);
        assertEquals(carrotTax(), voted.effects());
        Policy resolved = PolicyRules.castVote(g, voted, "c2", VoteChoice.YES, NOW + 2);
        assertEquals(PolicyStatus.PASSED, resolved.status());
        assertEquals(carrotTax(), resolved.effects());
    }

    @Test
    void decreePreservesEffects() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        Policy passed = PolicyRules.decree(g, p, "king:arthur", true, NOW + 1);
        assertEquals(carrotTax(), passed.effects());
    }

    @Test
    void equalsDistinguishesEffects() {
        Policy a = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, carrotTax());
        Policy b = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, DecreeEffects.empty());
        Policy a2 = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, carrotTax());
        assertEquals(a, a2);          // same effects → equal
        assertNotEquals(a, b);        // different effects → not equal
        assertEquals(a.hashCode(), a2.hashCode());
    }

    @Test
    void territoryProposePolicyCarriesEffects() {
        Territory t = new Territory("t1", "T", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
        ))).withGovernment(Government.monarchy("king:arthur"));
        Territory next = t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        assertEquals(carrotTax(), next.policy("tax").orElseThrow().effects());
    }
}
