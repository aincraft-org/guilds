package dev.mintychochip.territory.economy;

import java.util.Objects;

/** Result of an idempotent treasury expense request. */
public record ExpenseReport(
        ExpenseOutcome outcome,
        String territoryId,
        ExpenseKind kind,
        double amount,
        String idempotencyKey
) {
    public ExpenseReport {
        Objects.requireNonNull(outcome, "outcome");
    }
}
