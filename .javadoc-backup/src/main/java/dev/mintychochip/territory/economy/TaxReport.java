package dev.mintychochip.territory.economy;

import java.util.Objects;

/** Immutable outcome of a sale report. */
public record TaxReport(
        TaxOutcome outcome,
        String territoryId,
        String goodId,
        double ratePercent,
        double taxAmount
) {
    public TaxReport {
        Objects.requireNonNull(outcome, "outcome");
    }
}
