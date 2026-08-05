package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.models.Resident;
import org.aincraft.towny.plot.PlotTypeHandlerManager;
import org.aincraft.towny.plot.PlotTypeRegistry;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.ResidentService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for player movement events to handle town boundary notifications
 */
public class PlayerMovementListener implements Listener {

    private final TownyPlugin plugin;
    private final PlotService plotService;
    private final TownService townService;
    private final ResidentService residentService;
    private final PlotTypeHandlerManager plotTypeHandlerManager;
    private final PlotTypeRegistry plotTypeRegistry;

    // Track last known town for each player to detect boundary crossings
    private final Map<UUID, String> lastTownByPlayer = new ConcurrentHashMap<>();

    // Track last known plot type for each player to detect plot type changes
    private final Map<UUID, String> lastPlotTypeByPlayer = new ConcurrentHashMap<>();

    @Inject
    public PlayerMovementListener(TownyPlugin plugin, PlotService plotService, TownService townService,
                                  ResidentService residentService, PlotTypeHandlerManager plotTypeHandlerManager,
                                  PlotTypeRegistry plotTypeRegistry) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.townService = townService;
        this.residentService = residentService;
        this.plotTypeHandlerManager = plotTypeHandlerManager;
        this.plotTypeRegistry = plotTypeRegistry;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player moved between chunks (not just within the same chunk)
        if (event.getFrom().getChunk() == event.getTo().getChunk()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        if (playerUuid == null) {
            return;
        }

        try {
            // Get current location's town block
            int chunkX = event.getTo().getChunk().getX();
            int chunkZ = event.getTo().getChunk().getZ();
            String world = event.getTo().getWorld().getName();

            Optional<TownBlock> townBlock = plotService.getTownBlock(chunkX, chunkZ, world);
            String currentTownName = null;
            String plotInfo = null;
            String currentPlotType = null;

            if (townBlock.isPresent()) {
                // Player is in a town
                TownBlock block = townBlock.get();
                Optional<Town> town = townService.getTownById(block.getTownId());
                if (town.isPresent()) {
                    currentTownName = town.get().getName();
                    currentPlotType = block.getPlotType();

                    // Check if plot is owned by a resident
                    if (block.getOwnerId() != null) {
                        Optional<Resident> owner = residentService.getResident(block.getOwnerId());
                        if (owner.isPresent()) {
                            plotInfo = ChatColor.AQUA + owner.get().getName() + "'s " + ChatColor.GRAY + "Plot";
                        } else {
                            plotInfo = ChatColor.GRAY + "Owned Plot";
                        }
                    } else {
                        plotInfo = ChatColor.GREEN + "Town Plot";
                    }
                }
            } else {
                // Player is in wilderness
                currentPlotType = "wilderness";
            }

            // Check if player crossed a boundary
            String lastTownName = lastTownByPlayer.get(playerUuid);
            String lastPlotType = lastPlotTypeByPlayer.get(playerUuid);

            if (lastTownName == null && currentTownName == null) {
                // Still in wilderness, no change
                return;
            } else if (lastTownName == null && currentTownName != null) {
                // Entering town from wilderness
                String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                String message = ChatColor.GREEN + "Entering " + ChatColor.YELLOW + currentTownName +
                               ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName;
                sendActionBarMessage(player, message);
            } else if (lastTownName != null && currentTownName == null) {
                // Leaving town for wilderness
                sendActionBarMessage(player, ChatColor.GRAY + "Leaving " + ChatColor.YELLOW + lastTownName + ChatColor.GRAY + " for Wilderness");
            } else if (!lastTownName.equals(currentTownName)) {
                // Moving between different towns
                String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                String message = ChatColor.GREEN + "Entering " + ChatColor.YELLOW + currentTownName +
                               ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName;
                sendActionBarMessage(player, message);
            } else if (townBlock.isPresent()) {
                // Same town - check if plot type changed
                if (lastPlotType == null || !lastPlotType.equals(currentPlotType)) {
                    String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                    sendActionBarMessage(player, ChatColor.YELLOW + currentTownName + ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName);
                }
                // Don't show ownership info if we just showed plot type
            }

            // Check for plot type changes and dispatch events
            if (lastPlotType != null && !lastPlotType.equals(currentPlotType)) {
                // Player left the previous plot type
                if (townBlock.isPresent()) {
                    // Get the previous town block for the leave event
                    Optional<TownBlock> lastTownBlockOpt = plotService.getTownBlock(
                            event.getFrom().getChunk().getX(), event.getFrom().getChunk().getZ(), world);
                    if (lastTownBlockOpt.isPresent()) {
                        plotTypeHandlerManager.dispatchPlayerLeaveEvent(player, lastTownBlockOpt.get());
                    }
                }
            }

            if (currentPlotType != null && (lastPlotType == null || !lastPlotType.equals(currentPlotType))) {
                // Player entered a new plot type
                if (townBlock.isPresent()) {
                    plotTypeHandlerManager.dispatchPlayerEnterEvent(player, townBlock.get());
                }
            }

            // Update tracking data
            if (currentTownName != null) {
                lastTownByPlayer.put(playerUuid, currentTownName);
            } else {
                // Player is in wilderness, remove or set to null
                lastTownByPlayer.remove(playerUuid);
            }

            // Update last known plot type
            if (currentPlotType != null) {
                lastPlotTypeByPlayer.put(playerUuid, currentPlotType);
            } else {
                lastPlotTypeByPlayer.remove(playerUuid);
            }

        } catch (Exception e) {
            // Catch any exceptions to prevent crashes during movement
            plugin.getLogger().warning("Error in PlayerMoveEvent: " + e.getMessage());
        }
    }

    private void sendActionBarMessage(Player player, String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendActionBar(message);
            }
        }.runTask(plugin);
    }

    private String getPlotTypeDisplayName(String plotType) {
        if (plotType == null) return "Unknown";
        return plotTypeRegistry.getPlotType(plotType)
            .map(def -> def.getDisplayName())
            .orElse(plotType);
    }

    /**
     * Clean up stored data for offline players to prevent memory leaks
     */
    public void cleanupOfflinePlayer(UUID playerUuid) {
        lastTownByPlayer.remove(playerUuid);
        lastPlotTypeByPlayer.remove(playerUuid);
    }
}