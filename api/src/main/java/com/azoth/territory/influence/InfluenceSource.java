package com.azoth.territory.influence;

/** Activity event types that feed the influence race (spec §4). */
public enum InfluenceSource {
    /** A player-versus-player kill. */
    PVP_KILL,
    /** A player-versus-environment kill. */
    PVE_KILL,
    /** Breaking a block. */
    BLOCK_BREAK,
    /** Placing a block. */
    BLOCK_PLACE,
    /** Crafting an item. */
    CRAFT
}
