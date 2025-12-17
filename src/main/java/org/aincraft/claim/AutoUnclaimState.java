package org.aincraft.claim;

/**
 * Immutable state representing a player's auto-unclaim configuration.
 * Tracks whether auto-unclaim is enabled and if silent mode is active.
 */
public record AutoUnclaimState(boolean isEnabled, boolean isSilent) {

    /**
     * Creates an enabled auto-unclaim state with messages.
     */
    public static AutoUnclaimState enabled() {
        return new AutoUnclaimState(true, false);
    }

    /**
     * Creates an enabled auto-unclaim state in silent mode (no success messages).
     */
    public static AutoUnclaimState enabledSilent() {
        return new AutoUnclaimState(true, true);
    }

    /**
     * Creates a disabled auto-unclaim state.
     */
    public static AutoUnclaimState disabled() {
        return new AutoUnclaimState(false, false);
    }
}
