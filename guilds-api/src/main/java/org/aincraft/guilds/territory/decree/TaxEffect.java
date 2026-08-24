package org.aincraft.guilds.territory.decree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tax adjustment applied to a concrete set of good ids.
 * <p>
 * {@code taxDeltaPercentPoints} is an additive change in percentage points
 * (e.g. {@code 15} means +15 percentage points on the tax rate for each listed good).
 */
public record TaxEffect(List<String> goodIds, double taxDeltaPercentPoints) {

    public TaxEffect {
        if (goodIds == null || goodIds.isEmpty()) {
            throw new IllegalArgumentException("tax effect requires at least one good id");
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String id : goodIds) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("good id must not be blank");
            }
            ordered.add(Good.normalizeId(id));
        }
        goodIds = Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaxEffect that)) {
            return false;
        }
        return Double.compare(that.taxDeltaPercentPoints, taxDeltaPercentPoints) == 0
                && goodIds.equals(that.goodIds);
    }

    @Override
    public int hashCode() {
        return goodIds.hashCode() + Double.hashCode(taxDeltaPercentPoints);
    }

    @Override
    public String toString() {
        return "TaxEffect{goodIds=" + goodIds + ", taxDeltaPercentPoints=" + taxDeltaPercentPoints + '}';
    }
}
