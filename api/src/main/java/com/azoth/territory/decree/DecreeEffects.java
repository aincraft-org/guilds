package com.azoth.territory.decree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured decree/law effects produced by transcription (English → JSON model).
 * <p>
 * Schema version 1: a list of {@link TaxEffect}s. Empty means prose-only body with no machine effects.
 *
 * @param version schema version, or a default when non-positive
 * @param taxes tax effects in declaration order
 */
public record DecreeEffects(int version, List<TaxEffect> taxes) {
    /** Current structured-effects schema version. */
    public static final int SCHEMA_VERSION = 1;

    /** Normalizes the version and makes the tax list immutable. */
    public DecreeEffects {
        version = version <= 0 ? SCHEMA_VERSION : version;
        if (taxes == null || taxes.isEmpty()) {
            taxes = List.of();
        } else {
            taxes = Collections.unmodifiableList(new ArrayList<>(taxes));
        }
    }

    /**
     * Creates empty decree effects.
     *
     * @return empty effects
     */
    public static DecreeEffects empty() {
        return new DecreeEffects(SCHEMA_VERSION, List.of());
    }

    /**
     * Creates decree effects from tax effects.
     *
     * @param taxes tax effects
     * @return decree effects containing the taxes
     */
    public static DecreeEffects ofTaxes(List<TaxEffect> taxes) {
        return new DecreeEffects(SCHEMA_VERSION, taxes);
    }

    /**
     * Creates decree effects containing one tax effect.
     *
     * @param tax tax effect
     * @return decree effects containing the tax
     */
    public static DecreeEffects ofTax(TaxEffect tax) {
        Objects.requireNonNull(tax, "tax");
        return ofTaxes(List.of(tax));
    }

    /**
     * Indicates whether no tax effects are present.
     *
     * @return true when no tax effects are present
     */
    public boolean isEmpty() {
        return taxes.isEmpty();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(version, taxes);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "DecreeEffects{version=" + version + ", taxes=" + taxes.size() + '}';
    }
}
