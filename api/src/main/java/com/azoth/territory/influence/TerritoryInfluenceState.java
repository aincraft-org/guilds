package com.azoth.territory.influence;

import java.util.List;

/**
 * Read-only snapshot of the influence race state for one territory.
 *
 * @param territoryId territory identifier
 * @param ownerGuildId current owning guild identifier, if any
 * @param cooldownUntilEpochMs cooldown end time in epoch milliseconds
 * @param bars attacking guild influence bars
 * @param declaration active takeover declaration, if any
 */
public record TerritoryInfluenceState(
        String territoryId,
        String ownerGuildId,
        long cooldownUntilEpochMs,
        List<InfluenceBar> bars,
        Declaration declaration
) {
    /** Validates and copies the influence state components. */
    public TerritoryInfluenceState {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId is required");
        }
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
