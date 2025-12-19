package org.aincraft.subregion;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.aincraft.subregion.events.PlayerEnterSubregionEvent;
import org.aincraft.subregion.events.PlayerExitSubregionEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Tracks player movement and fires enter/exit events for subregions.
 * Uses block-level position checks for performance.
 */
public class RegionMovementTracker implements Listener {
    private final SubregionService subregionService;
    private final Map<UUID, UUID> playerCurrentRegion = new ConcurrentHashMap<>();

    @Inject
    public RegionMovementTracker(SubregionService subregionService) {
        this.subregionService = subregionService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerCurrentRegion.remove(event.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location from, Location to) {
        if (to == null) return;

        // Only check on block-level changes (performance optimization)
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID previousRegionId = playerCurrentRegion.get(playerId);

        // Get current region at new location
        Optional<Subregion> currentRegionOpt = subregionService.getSubregionAt(to);
        UUID currentRegionId = currentRegionOpt.map(Subregion::getId).orElse(null);

        // No change in region
        if (java.util.Objects.equals(previousRegionId, currentRegionId)) {
            return;
        }

        // Player exited a region
        if (previousRegionId != null) {
            subregionService.getSubregionById(previousRegionId).ifPresent(region -> {
                PlayerExitSubregionEvent exitEvent = new PlayerExitSubregionEvent(player, region, from, to);
                Bukkit.getPluginManager().callEvent(exitEvent);
            });
        }

        // Player entered a new region
        if (currentRegionOpt.isPresent()) {
            Subregion region = currentRegionOpt.get();
            PlayerEnterSubregionEvent enterEvent = new PlayerEnterSubregionEvent(player, region, from, to);
            Bukkit.getPluginManager().callEvent(enterEvent);
            playerCurrentRegion.put(playerId, region.getId());
        } else {
            playerCurrentRegion.remove(playerId);
        }
    }

    /**
     * Gets the current region ID for a player (for external use).
     */
    public Optional<UUID> getCurrentRegionId(UUID playerId) {
        return Optional.ofNullable(playerCurrentRegion.get(playerId));
    }

    /**
     * Clears all tracked data. Used for cleanup on disable.
     */
    public void clearAll() {
        playerCurrentRegion.clear();
    }
}
