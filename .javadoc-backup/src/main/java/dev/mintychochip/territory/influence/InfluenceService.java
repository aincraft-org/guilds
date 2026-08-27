package dev.mintychochip.territory.influence;

import java.util.List;
import java.util.Optional;

/**
 * Public influence-race surface for external consumers (queries + declaration
 * lifecycle). Accrual is engine-internal and driven by the Paper layer.
 */
public interface InfluenceService {

    /**
     * Race state for one territory, if any influence state exists.
     *
     * @param territoryId territory identifier
     * @return the territory race state, if present
     */
    Optional<TerritoryInfluenceState> influence(String territoryId);

    /**
     * Race state for every territory with recorded influence state.
     *
     * @return all recorded territory race states
     */
    List<TerritoryInfluenceState> all();

    /**
     * Declare a takeover on behalf of {@code guildId}; {@code authorityId}
     * must hold a seat in that guild's government. {@code nowEpochMs} is the
     * authoritative clock (injected for testability).
     *
     * @param territoryId territory identifier
     * @param guildId guild making the declaration
     * @param authorityId authority making the request
     * @param nowEpochMs authoritative current time in epoch milliseconds
     * @return the declaration outcome
     */
    DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /**
     * Cancel the guild's own active declaration on a territory.
     *
     * @param territoryId territory identifier
     * @param guildId guild whose declaration is cancelled
     * @param authorityId authority making the request
     * @param nowEpochMs authoritative current time in epoch milliseconds
     * @return the cancellation outcome
     */
    DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /**
     * True when the guild may currently declare (eligible + at cap + race open).
     *
     * @param territoryId territory identifier
     * @param guildId guild being checked
     * @param nowEpochMs authoritative current time in epoch milliseconds
     * @return whether the guild may declare
     */
    boolean isDeclarable(String territoryId, String guildId, long nowEpochMs);

    /**
     * True while the post-flip cooldown blocks a new race on the territory.
     *
     * @param territoryId territory identifier
     * @param nowEpochMs authoritative current time in epoch milliseconds
     * @return whether cooldown is active
     */
    boolean isCooldownActive(String territoryId, long nowEpochMs);
}
