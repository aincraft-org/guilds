package com.azoth.territory.standing;

import java.util.List;

/** Read snapshot of standing for one territory. */
public record TerritoryStandingState(
        String territoryId,
        String ownerGuildId,
        List<StandingBar> bars
) {
}
