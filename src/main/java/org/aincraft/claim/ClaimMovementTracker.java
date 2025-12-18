package org.aincraft.claim;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.claim.events.PlayerExitClaimEvent;
import org.aincraft.service.TerritoryService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
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
 * Tracks player movement across guild claims at chunk level.
 * Fires PlayerEnterClaimEvent and PlayerExitClaimEvent when crossing claim boundaries.
 * Works in conjunction with subregion tracking for complete context.
 * Uses block-level movement checks for performance optimization.
 */
public class ClaimMovementTracker implements Listener {
    private final TerritoryService territoryService;
    private final SubregionService subregionService;

    // Track: player UUID -> current claim state
    private final Map<UUID, ClaimState> playerCurrentClaim = new ConcurrentHashMap<>();

    @Inject
    public ClaimMovementTracker(TerritoryService territoryService, SubregionService subregionService) {
        this.territoryService = Objects.requireNonNull(territoryService, "Territory service cannot be null");
        this.subregionService = Objects.requireNonNull(subregionService, "Subregion service cannot be null");
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
        playerCurrentClaim.remove(event.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location from, Location to) {
        if (to == null) return;

        // Block-level optimization - only check on actual chunk boundary or block changes
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Determine new claim state
        ClaimState newState = calculateClaimState(to);
        ClaimState previousState = playerCurrentClaim.get(playerId);

        // No change - same ownership and type
        if (newState.equals(previousState)) {
            return;
        }

        // Fire exit event if player leaves a claim (ownership change)
        if (previousState != null && newState.ownershipChangedFrom(previousState)) {
            PlayerExitClaimEvent exitEvent = new PlayerExitClaimEvent(
                player, previousState, newState, from, to
            );
            Bukkit.getPluginManager().callEvent(exitEvent);
        }

        // Fire enter event if entering new claim or type changed
        PlayerEnterClaimEvent enterEvent = new PlayerEnterClaimEvent(
            player, newState, previousState, from, to
        );
        Bukkit.getPluginManager().callEvent(enterEvent);

        // Update tracking
        playerCurrentClaim.put(playerId, newState);
    }

    /**
     * Calculates current claim state at a location by checking:
     * 1. Chunk ownership (guild)
     * 2. Subregion type (if inside subregion)
     */
    private ClaimState calculateClaimState(Location location) {
        // Get chunk owner
        ChunkKey chunkKey = ChunkKey.from(location.getChunk());
        Guild owner = territoryService.getChunkOwner(chunkKey);

        // If no owner, it's wilderness
        if (owner == null) {
            return ClaimState.wilderness();
        }

        // Owner exists - get subregion type in this guild's chunk
        Optional<Subregion> subregionOpt = subregionService.getSubregionAt(location);
        String typeId = subregionOpt.map(Subregion::getType).orElse(null);

        return ClaimState.ofGuild(owner, typeId);
    }

    /**
     * Gets the current claim state for a player.
     */
    public Optional<ClaimState> getCurrentClaimState(UUID playerId) {
        return Optional.ofNullable(playerCurrentClaim.get(playerId));
    }

    /**
     * Clears all tracked data. Used for cleanup on plugin disable.
     */
    public void clearAll() {
        playerCurrentClaim.clear();
    }
}
