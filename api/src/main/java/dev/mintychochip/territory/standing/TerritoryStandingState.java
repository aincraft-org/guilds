package dev.mintychochip.territory.standing;

import java.util.List;

/**
 * Read snapshot of standing for one territory.
 *
 * @param territoryId territory identifier
 * @param ownerGuildId owning guild identifier
 * @param bars standing bars recorded for the territory
 */
public record TerritoryStandingState(
        String territoryId,
        String ownerGuildId,
        List<StandingBar> bars
) {
}
