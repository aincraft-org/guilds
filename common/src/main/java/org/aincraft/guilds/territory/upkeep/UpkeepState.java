package org.aincraft.guilds.territory.upkeep;

import org.aincraft.guilds.territory.economy.ExpenseOutcome;

import java.util.Objects;

/** Durable state for one territory's recurring upkeep period. */
public record UpkeepState(
        String territoryId,
        double amount,
        UpkeepStatus status,
        long nextDueEpochMs,
        long graceDeadlineEpochMs,
        String lastPeriodKey,
        ExpenseOutcome lastOutcome
) {
    public UpkeepState {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId must not be blank");
        }
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        Objects.requireNonNull(status, "status");
        if (nextDueEpochMs < 0L || graceDeadlineEpochMs < 0L) {
            throw new IllegalArgumentException("upkeep timestamps must be non-negative");
        }
        if (lastPeriodKey != null && lastPeriodKey.isBlank()) {
            throw new IllegalArgumentException("lastPeriodKey must be null or non-blank");
        }
    }
}
