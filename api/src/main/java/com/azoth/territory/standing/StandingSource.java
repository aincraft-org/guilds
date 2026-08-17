package com.azoth.territory.standing;

/**
 * Activity types that accumulate territory standing (spec §4).
 */
public enum StandingSource {
    /** Standing gained from a player-versus-player kill. */
    PVP_KILL,
    /** Standing gained from a player-versus-environment kill. */
    PVE_KILL,
    /** Standing gained from breaking a block. */
    BLOCK_BREAK
}
