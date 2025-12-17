package org.aincraft.subregion;

import com.google.inject.Singleton;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles client-side block indicators for pos1/pos2 selection.
 * Shows fake blocks to players without affecting the actual world.
 */
@Singleton
public class SelectionVisualizer {
    private static final Material POS1_MATERIAL = Material.GLOWSTONE;
    private static final Material POS2_MATERIAL = Material.REDSTONE_BLOCK;

    private final Map<UUID, IndicatorState> indicators = new ConcurrentHashMap<>();

    /**
     * Shows the pos1 indicator at the given location.
     * Clears any previous pos1 indicator first.
     */
    public void showPos1(Player player, Location loc) {
        UUID playerId = player.getUniqueId();
        IndicatorState current = indicators.get(playerId);

        // Clear previous pos1 indicator
        if (current != null && current.pos1() != null) {
            restoreBlock(player, current.pos1(), current.originalPos1());
        }

        // Store original block data and show indicator
        BlockData original = loc.getBlock().getBlockData().clone();
        player.sendBlockChange(loc, POS1_MATERIAL.createBlockData());

        // Update state
        indicators.compute(playerId, (id, state) -> {
            if (state == null) {
                return new IndicatorState(loc, original, null, null);
            }
            return new IndicatorState(loc, original, state.pos2(), state.originalPos2());
        });
    }

    /**
     * Shows the pos2 indicator at the given location.
     * Clears any previous pos2 indicator first.
     */
    public void showPos2(Player player, Location loc) {
        UUID playerId = player.getUniqueId();
        IndicatorState current = indicators.get(playerId);

        // Clear previous pos2 indicator
        if (current != null && current.pos2() != null) {
            restoreBlock(player, current.pos2(), current.originalPos2());
        }

        // Store original block data and show indicator
        BlockData original = loc.getBlock().getBlockData().clone();
        player.sendBlockChange(loc, POS2_MATERIAL.createBlockData());

        // Update state
        indicators.compute(playerId, (id, state) -> {
            if (state == null) {
                return new IndicatorState(null, null, loc, original);
            }
            return new IndicatorState(state.pos1(), state.originalPos1(), loc, original);
        });
    }

    /**
     * Clears all indicators for a player, restoring original blocks.
     */
    public void clearIndicators(Player player) {
        UUID playerId = player.getUniqueId();
        IndicatorState state = indicators.remove(playerId);

        if (state != null) {
            if (state.pos1() != null) {
                restoreBlock(player, state.pos1(), state.originalPos1());
            }
            if (state.pos2() != null) {
                restoreBlock(player, state.pos2(), state.originalPos2());
            }
        }
    }

    /**
     * Clears indicators for a player by UUID (for offline cleanup).
     */
    public void clearIndicators(UUID playerId) {
        indicators.remove(playerId);
    }

    /**
     * Checks if a player has any active indicators.
     */
    public boolean hasIndicators(UUID playerId) {
        return indicators.containsKey(playerId);
    }

    /**
     * Clears all indicators (for plugin disable).
     */
    public void clearAll() {
        indicators.clear();
    }

    /**
     * Refreshes indicators for a player (re-sends fake blocks).
     * Useful if the player moved far away and came back.
     */
    public void refreshIndicators(Player player) {
        IndicatorState state = indicators.get(player.getUniqueId());
        if (state != null) {
            if (state.pos1() != null) {
                player.sendBlockChange(state.pos1(), POS1_MATERIAL.createBlockData());
            }
            if (state.pos2() != null) {
                player.sendBlockChange(state.pos2(), POS2_MATERIAL.createBlockData());
            }
        }
    }

    private void restoreBlock(Player player, Location loc, BlockData original) {
        if (original != null) {
            player.sendBlockChange(loc, original);
        } else {
            // Fallback: send current world block data
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    /**
     * Represents the indicator state for a player.
     */
    public record IndicatorState(
            Location pos1, BlockData originalPos1,
            Location pos2, BlockData originalPos2
    ) {}
}
