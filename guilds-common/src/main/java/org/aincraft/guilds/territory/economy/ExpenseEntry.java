package org.aincraft.guilds.territory.economy;

import java.util.Objects;

/** Durable idempotency record for one treasury expense request. */
public record ExpenseEntry(
        String idempotencyKey,
        String territoryId,
        ExpenseKind kind,
        double amount,
        ExpenseJournalState state,
        ExpenseOutcome outcome
) {
    public ExpenseEntry {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be finite and positive");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outcome, "outcome");
    }
}
