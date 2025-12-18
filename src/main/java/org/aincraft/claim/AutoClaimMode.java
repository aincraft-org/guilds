package org.aincraft.claim;

/**
 * Represents the auto-claim mode for a player.
 * Determines whether the player automatically claims or unclaims chunks on entry.
 */
public enum AutoClaimMode {
    /**
     * No auto action - manual claiming/unclaiming only.
     */
    OFF,

    /**
     * Automatically claim wilderness chunks when entering them.
     */
    AUTO_CLAIM,

    /**
     * Automatically unclaim owned chunks when entering them.
     */
    AUTO_UNCLAIM;
}
