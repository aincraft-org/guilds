package org.aincraft.subregion;

import com.google.inject.Singleton;
import org.bukkit.Location;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player selections for subregion creation.
 * Thread-safe for concurrent access.
 */
@Singleton
public class SelectionManager {
    private final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();

    /**
     * Sets position 1 for a player's selection.
     */
    public void setPos1(UUID playerId, Location location) {
        selections.compute(playerId, (id, existing) -> {
            if (existing == null) {
                return new PlayerSelection(location, null, System.currentTimeMillis());
            }
            return new PlayerSelection(location, existing.pos2(), System.currentTimeMillis());
        });
    }

    /**
     * Sets position 2 for a player's selection.
     */
    public void setPos2(UUID playerId, Location location) {
        selections.compute(playerId, (id, existing) -> {
            if (existing == null) {
                return new PlayerSelection(null, location, System.currentTimeMillis());
            }
            return new PlayerSelection(existing.pos1(), location, System.currentTimeMillis());
        });
    }

    /**
     * Gets a player's current selection.
     */
    public Optional<PlayerSelection> getSelection(UUID playerId) {
        return Optional.ofNullable(selections.get(playerId));
    }

    /**
     * Checks if a player has a complete selection (both positions set).
     */
    public boolean hasCompleteSelection(UUID playerId) {
        PlayerSelection sel = selections.get(playerId);
        return sel != null && sel.isComplete();
    }

    /**
     * Clears a player's selection.
     */
    public void clearSelection(UUID playerId) {
        selections.remove(playerId);
    }

    /**
     * Clears all selections (for plugin disable).
     */
    public void clearAll() {
        selections.clear();
    }

    /**
     * Clears expired selections (older than 30 minutes).
     */
    public void clearExpired() {
        long threshold = System.currentTimeMillis() - (30 * 60 * 1000);
        selections.entrySet().removeIf(entry -> entry.getValue().createdAt() < threshold);
    }

    /**
     * Represents a player's selection state.
     */
    public record PlayerSelection(Location pos1, Location pos2, long createdAt) {
        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        public boolean hasPos1() {
            return pos1 != null;
        }

        public boolean hasPos2() {
            return pos2 != null;
        }
    }
}
