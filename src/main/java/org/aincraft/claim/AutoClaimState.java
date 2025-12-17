package org.aincraft.claim;

/**
 * Immutable state representing a player's auto-claim configuration.
 * Tracks whether auto-claim is enabled and if silent mode is active.
 */
public record AutoClaimState(boolean isEnabled, boolean isSilent) {

    /**
     * Creates an enabled auto-claim state with messages.
     */
    public static AutoClaimState enabled() {
        return new AutoClaimState(true, false);
    }

    /**
     * Creates an enabled auto-claim state in silent mode (no success messages).
     */
    public static AutoClaimState enabledSilent() {
        return new AutoClaimState(true, true);
    }

    /**
     * Creates a disabled auto-claim state.
     */
    public static AutoClaimState disabled() {
        return new AutoClaimState(false, false);
    }
}
