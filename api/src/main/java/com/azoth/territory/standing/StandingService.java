package com.azoth.territory.standing;

import java.util.List;
import java.util.Optional;

/**
 * Public standing surface for external consumers (queries + admin).
 * Accrual is engine-internal and driven by the Paper layer.
 */
public interface StandingService {

    /** Standing state for one territory, if any standing exists. */
    Optional<TerritoryStandingState> standing(String territoryId);

    /** Standing state for every territory with recorded standing. */
    List<TerritoryStandingState> all();

    /** Harvest multiplier for {@code guildId} on {@code territoryId} (1.0 when none). */
    double harvestMultiplierFor(String territoryId, String guildId);

    /** Max influence multiplier across all territories {@code guildId} governs (1.0 when none). */
    double influenceMultiplierFor(String guildId);

    /** Highest tier satisfied by {@code guildId}'s standing on the territory, if any state exists. */
    Optional<StandingTier> tierFor(String territoryId, String guildId);

    /** Admin: set a guild's standing bar on a territory (clamped to [0, cap]). */
    boolean adminSet(String territoryId, String guildId, double value);

    /** Admin: drop all standing state for a territory. */
    boolean adminReset(String territoryId);
}
