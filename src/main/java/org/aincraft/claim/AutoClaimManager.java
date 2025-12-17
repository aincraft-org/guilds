package org.aincraft.claim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player auto-claim state.
 * Thread-safe using ConcurrentHashMap for concurrent player access.
 */
public class AutoClaimManager {
    private final Map<UUID, AutoClaimState> playerAutoClaimState = new ConcurrentHashMap<>();

    /**
     * Toggles auto-claim for a player. If currently disabled, enables it.
     * If currently enabled, disables it.
     *
     * @param playerId the player's UUID
     * @param silent whether to enable silent mode (no success messages)
     * @return the new auto-claim state after toggling
     */
    public AutoClaimState toggleAutoClaim(UUID playerId, boolean silent) {
        AutoClaimState currentState = playerAutoClaimState.get(playerId);

        if (currentState == null || !currentState.isEnabled()) {
            // Currently disabled -> enable
            AutoClaimState newState = silent ? AutoClaimState.enabledSilent() : AutoClaimState.enabled();
            playerAutoClaimState.put(playerId, newState);
            return newState;
        } else {
            // Currently enabled -> disable
            AutoClaimState disabledState = AutoClaimState.disabled();
            playerAutoClaimState.put(playerId, disabledState);
            return disabledState;
        }
    }

    /**
     * Checks if auto-claim is enabled for a player.
     *
     * @param playerId the player's UUID
     * @return true if auto-claim is enabled, false otherwise
     */
    public boolean isAutoClaimEnabled(UUID playerId) {
        AutoClaimState state = playerAutoClaimState.get(playerId);
        return state != null && state.isEnabled();
    }

    /**
     * Checks if a player has silent mode enabled.
     *
     * @param playerId the player's UUID
     * @return true if silent mode is active, false otherwise
     */
    public boolean isSilentMode(UUID playerId) {
        AutoClaimState state = playerAutoClaimState.get(playerId);
        return state != null && state.isSilent();
    }

    /**
     * Forces auto-claim to be disabled for a player.
     * Used when auto-claim encounters a failure.
     *
     * @param playerId the player's UUID
     */
    public void disableAutoClaim(UUID playerId) {
        playerAutoClaimState.put(playerId, AutoClaimState.disabled());
    }

    /**
     * Gets the current auto-claim state for a player.
     *
     * @param playerId the player's UUID
     * @return the auto-claim state, or null if not set
     */
    public AutoClaimState getAutoClaimState(UUID playerId) {
        return playerAutoClaimState.get(playerId);
    }

    /**
     * Clears all tracked auto-claim state. Used for cleanup on plugin disable.
     */
    public void clearAll() {
        playerAutoClaimState.clear();
    }
}
