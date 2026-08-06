package com.azoth.territory.influence;

import java.util.List;

/** Read-only snapshot of the influence race state for one territory. */
public record TerritoryInfluenceState(
        String territoryId,
        String ownerGuildId,
        long cooldownUntilEpochMs,
        List<InfluenceBar> bars,
        Declaration declaration
) {
    public TerritoryInfluenceState {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId is required");
        }
        bars = bars == null ? List.of() : List.copyOf(bars);
    }
}
