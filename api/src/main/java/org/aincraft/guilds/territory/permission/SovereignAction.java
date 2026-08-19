package org.aincraft.guilds.territory.permission;

/**
 * Actions whose formal authority is decided by government form + filled seats.
 */
public enum SovereignAction {
    /** Add/remove members of a guild or alliance. */
    MANAGE_MEMBERSHIP,
    /** Propose / decree / vote path eligibility (policy authority). */
    SET_POLICY,
    /** Break blocks in governed space. */
    BREAK_BLOCK,
    /** Place blocks in governed space. */
    PLACE_BLOCK,
    /** Interact with blocks/entities (containers, doors, buttons, armor stands, vehicles, …). */
    INTERACT
}
