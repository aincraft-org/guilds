package org.aincraft.claim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player auto-unclaim state.
 * Thread-safe using ConcurrentHashMap for concurrent player access.
 */
public class AutoUnclaimManager {
    private final Map<UUID, AutoUnclaimState> playerAutoUnclaimState = new ConcurrentHashMap<>();

    /**
     * Toggles auto-unclaim for a player. If currently disabled, enables it.
     * If currently enabled, disables it.
     *
     * @param playerId the player's UUID
     * @param silent whether to enable silent mode (no success messages)
     * @return the new auto-unclaim state after toggling
     */
    public AutoUnclaimState toggleAutoUnclaim(UUID playerId, boolean silent) {
        AutoUnclaimState currentState = playerAutoUnclaimState.get(playerId);

        if (currentState == null || !currentState.isEnabled()) {
            // Currently disabled -> enable
            AutoUnclaimState newState = silent ? AutoUnclaimState.enabledSilent() : AutoUnclaimState.enabled();
            playerAutoUnclaimState.put(playerId, newState);
            return newState;
        } else {
            // Currently enabled -> disable
            AutoUnclaimState disabledState = AutoUnclaimState.disabled();
            playerAutoUnclaimState.put(playerId, disabledState);
            return disabledState;
        }
    }

    /**
     * Checks if auto-unclaim is enabled for a player.
     *
     * @param playerId the player's UUID
     * @return true if auto-unclaim is enabled, false otherwise
     */
    public boolean isAutoUnclaimEnabled(UUID playerId) {
        AutoUnclaimState state = playerAutoUnclaimState.get(playerId);
        return state != null && state.isEnabled();
    }

    /**
     * Checks if a player has silent mode enabled.
     *
     * @param playerId the player's UUID
     * @return true if silent mode is active, false otherwise
     */
    public boolean isSilentMode(UUID playerId) {
        AutoUnclaimState state = playerAutoUnclaimState.get(playerId);
        return state != null && state.isSilent();
    }

    /**
     * Forces auto-unclaim to be disabled for a player.
     * Used when auto-unclaim encounters a failure.
     *
     * @param playerId the player's UUID
     */
    public void disableAutoUnclaim(UUID playerId) {
        playerAutoUnclaimState.put(playerId, AutoUnclaimState.disabled());
    }

    /**
     * Gets the current auto-unclaim state for a player.
     *
     * @param playerId the player's UUID
     * @return the auto-unclaim state, or null if not set
     */
    public AutoUnclaimState getAutoUnclaimState(UUID playerId) {
        return playerAutoUnclaimState.get(playerId);
    }

    /**
     * Clears all tracked auto-unclaim state. Used for cleanup on plugin disable.
     */
    public void clearAll() {
        playerAutoUnclaimState.clear();
    }
}
