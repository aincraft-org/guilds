package org.aincraft.guilds.territory.influence;

import java.util.List;
import java.util.Optional;

/**
 * Public influence-race surface for external consumers (queries + declaration
 * lifecycle). Accrual is engine-internal and driven by the Paper layer.
 */
public interface InfluenceService {

    /** Race state for one territory, if any influence state exists. */
    Optional<TerritoryInfluenceState> influence(String territoryId);

    /** Race state for every territory with recorded influence state. */
    List<TerritoryInfluenceState> all();

    /**
     * Declare a takeover on behalf of {@code guildId}; {@code authorityId}
     * must hold a seat in that guild's government. {@code nowEpochMs} is the
     * authoritative clock (injected for testability).
     */
    DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /** Cancel the guild's own active declaration on a territory. */
    DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs);

    /** True when the guild may currently declare (eligible + at cap + race open). */
    boolean isDeclarable(String territoryId, String guildId, long nowEpochMs);

    /** True while the post-flip cooldown blocks a new race on the territory. */
    boolean isCooldownActive(String territoryId, long nowEpochMs);
}
