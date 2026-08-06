package com.azoth.territory.decree;

import com.azoth.territory.model.Policy;
import com.azoth.territory.model.PolicyStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Interprets structured {@link DecreeEffects} into queryable tax rates by good id.
 * <p>
 * Semantics: each {@link TaxEffect#taxDeltaPercentPoints()} is applied additively from a base of 0.
 * A single +15 effect yields tax rate 15.0 for each listed good (percentage points).
 * Does not re-parse English — only the structured payload.
 */
public final class DecreeEffectsInterpreter {
    private DecreeEffectsInterpreter() {
    }

    /**
     * Tax rate (percentage points) for each good mentioned in the effects, starting from 0.
     */
    public static Map<String, Double> taxRatesByGoodId(DecreeEffects effects) {
        if (effects == null || effects.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> rates = new LinkedHashMap<>();
        for (TaxEffect t : effects.taxes()) {
            double delta = t.taxDeltaPercentPoints();
            for (String goodId : t.goodIds()) {
                rates.merge(goodId, delta, Double::sum);
            }
        }
        return Collections.unmodifiableMap(rates);
    }

    public static Optional<Double> taxRateFor(DecreeEffects effects, String goodId) {
        if (goodId == null || goodId.isBlank()) {
            return Optional.empty();
        }
        Double rate = taxRatesByGoodId(effects).get(Good.normalizeId(goodId));
        return Optional.ofNullable(rate);
    }

    /**
     * Aggregate tax rates from PASSED policies that carry structured effects.
     * Rejected/proposed policies do not contribute.
     * <p>
     * Structured {@link DecreeEffects} are not yet attached to {@link Policy};
     * until that wiring lands, this returns an empty map (PASSED status is still validated).
     */
    public static Map<String, Double> taxRatesFromPolicies(Collection<Policy> policies) {
        if (policies == null || policies.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> merged = new LinkedHashMap<>();
        for (Policy p : policies) {
            Objects.requireNonNull(p, "policy");
            if (p.status() != PolicyStatus.PASSED) {
                continue;
            }
            for (Map.Entry<String, Double> e : taxRatesByGoodId(p.effects()).entrySet()) {
                merged.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        return Collections.unmodifiableMap(merged);
    }
}
