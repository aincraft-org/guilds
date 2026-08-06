package com.azoth.territory.decree;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.Policy;
import com.azoth.territory.model.PolicyRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Completion of the taxRatesFromPolicies stub: PASSED-only, additive across policies. */
class DecreeEffectsInterpreterTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final Government MONARCHY = Government.monarchy("king:arthur");

    private static DecreeEffects taxOn(String good, double delta) {
        return DecreeEffects.ofTax(new TaxEffect(List.of(good), delta));
    }

    private static Policy passed(String id, DecreeEffects effects) {
        return PolicyRules.decree(
                MONARCHY,
                PolicyRules.propose(MONARCHY, id, id, "B", "king:arthur", NOW, effects),
                "king:arthur", true, NOW + 1
        );
    }

    @Test
    void nullOrEmptyPoliciesYieldsEmptyMap() {
        assertTrue(DecreeEffectsInterpreter.taxRatesFromPolicies(null).isEmpty());
        assertTrue(DecreeEffectsInterpreter.taxRatesFromPolicies(List.of()).isEmpty());
    }

    @Test
    void passedPolicyContributesItsRates() {
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(
                List.of(passed("p1", taxOn("carrot", 15.0))));
        assertEquals(Map.of("carrot", 15.0), rates);
    }

    @Test
    void rejectedAndProposedPoliciesDoNotContribute() {
        Policy passedP = passed("p1", taxOn("carrot", 15.0));
        Policy proposed = PolicyRules.propose(MONARCHY, "p2", "p2", "B", "king:arthur", NOW, taxOn("potato", 10.0));
        Policy rejected = PolicyRules.decree(
                MONARCHY,
                PolicyRules.propose(MONARCHY, "p3", "p3", "B", "king:arthur", NOW, taxOn("onion", 5.0)),
                "king:arthur", false, NOW + 1
        );
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(
                List.of(passedP, proposed, rejected));
        assertEquals(Map.of("carrot", 15.0), rates);
    }

    @Test
    void multiplePassedPoliciesMergeAdditively() {
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(List.of(
                passed("p1", taxOn("carrot", 15.0)),
                passed("p2", taxOn("carrot", 5.0)),
                passed("p3", taxOn("potato", 10.0))
        ));
        assertEquals(Map.of("carrot", 20.0, "potato", 10.0), rates);
    }

    @Test
    void emptyEffectsPolicyContributesNothing() {
        Policy passedP = passed("p1", DecreeEffects.empty());
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(List.of(passedP));
        assertTrue(rates.isEmpty());
    }
}
