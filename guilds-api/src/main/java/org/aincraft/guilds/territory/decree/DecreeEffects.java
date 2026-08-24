package org.aincraft.guilds.territory.decree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured decree/law effects produced by transcription (English → JSON model).
 * <p>
 * Schema version 1: a list of {@link TaxEffect}s. Empty means prose-only body with no machine effects.
 */
public record DecreeEffects(int version, List<TaxEffect> taxes) {
    public static final int SCHEMA_VERSION = 1;

    public DecreeEffects {
        version = version <= 0 ? SCHEMA_VERSION : version;
        if (taxes == null || taxes.isEmpty()) {
            taxes = List.of();
        } else {
            taxes = Collections.unmodifiableList(new ArrayList<>(taxes));
        }
    }

    public static DecreeEffects empty() {
        return new DecreeEffects(SCHEMA_VERSION, List.of());
    }

    public static DecreeEffects ofTaxes(List<TaxEffect> taxes) {
        return new DecreeEffects(SCHEMA_VERSION, taxes);
    }

    public static DecreeEffects ofTax(TaxEffect tax) {
        Objects.requireNonNull(tax, "tax");
        return ofTaxes(List.of(tax));
    }

    public boolean isEmpty() {
        return taxes.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DecreeEffects that)) {
            return false;
        }
        return version == that.version && taxes.equals(that.taxes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, taxes);
    }

    @Override
    public String toString() {
        return "DecreeEffects{version=" + version + ", taxes=" + taxes.size() + '}';
    }
}
