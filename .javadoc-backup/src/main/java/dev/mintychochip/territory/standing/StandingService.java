package dev.mintychochip.territory.standing;

import java.util.List;
import java.util.Optional;

/**
 * Public standing surface for external consumers (queries + admin).
 * Accrual is engine-internal and driven by the Paper layer.
 */
public interface StandingService {

    /**
     * Standing state for one territory, if any standing exists.
     *
     * @param territoryId territory identifier
     * @return standing state when recorded standing exists
     */
    Optional<TerritoryStandingState> standing(String territoryId);
    /**
     * Standing state for every territory with recorded standing.
     *
     * @return standing states for all territories with recorded standing
     */
    List<TerritoryStandingState> all();

    /**
     * Harvest multiplier for {@code guildId} on {@code territoryId} (1.0 when none).
     *
     * @param territoryId territory identifier
     * @param guildId guild identifier
     * @return harvest multiplier for the guild on the territory
     */
    double harvestMultiplierFor(String territoryId, String guildId);

    /**
     * Max influence multiplier across all territories {@code guildId} governs (1.0 when none).
     *
     * @param guildId guild identifier
     * @return maximum influence multiplier across governed territories
     */
    double influenceMultiplierFor(String guildId);

    /**
     * Highest tier satisfied by {@code guildId}'s standing on the territory, if any state exists.
     *
     * @param territoryId territory identifier
     * @param guildId guild identifier
     * @return highest satisfied tier when standing state exists
     */
    Optional<StandingTier> tierFor(String territoryId, String guildId);

    /**
     * Admin: set a guild's standing bar on a territory (clamped to [0, cap]).
     *
     * @param territoryId territory identifier
     * @param guildId guild identifier
     * @param value requested standing value
     * @return {@code true} when the standing bar was set
     */
    boolean adminSet(String territoryId, String guildId, double value);

    /**
     * Admin: drop all standing state for a territory.
     *
     * @param territoryId territory identifier
     * @return {@code true} when standing state was reset
     */
    boolean adminReset(String territoryId);
}
