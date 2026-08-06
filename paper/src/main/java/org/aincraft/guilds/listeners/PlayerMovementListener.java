package org.aincraft.guilds.listeners;


import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.plot.PlotTypeHandlerManager;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for player movement events to handle guild boundary notifications
 */
public class PlayerMovementListener implements Listener {

    private final JavaPlugin plugin;
    private final PlotService plotService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final PlotTypeHandlerManager plotTypeHandlerManager;
    private final PlotTypeRegistry plotTypeRegistry;

    // Track last known guild for each player to detect boundary crossings
    private final Map<UUID, String> lastGuildByPlayer = new ConcurrentHashMap<>();

    // Track last known plot type for each player to detect plot type changes
    private final Map<UUID, String> lastPlotTypeByPlayer = new ConcurrentHashMap<>();


    public PlayerMovementListener(JavaPlugin plugin, PlotService plotService, GuildService guildService,
                                  ResidentService residentService, PlotTypeHandlerManager plotTypeHandlerManager,
                                  PlotTypeRegistry plotTypeRegistry) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.guildService = guildService;
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
            // Get current location's guild block
            int chunkX = event.getTo().getChunk().getX();
            int chunkZ = event.getTo().getChunk().getZ();
            String world = event.getTo().getWorld().getName();

            Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
            String currentGuildName = null;
            String plotInfo = null;
            String currentPlotType = null;

            if (guildBlock.isPresent()) {
                // Player is in a guild
                GuildBlock block = guildBlock.get();
                Optional<Guild> guild = guildService.getGuildById(block.getGuildId());
                if (guild.isPresent()) {
                    currentGuildName = guild.get().getName();
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
            String lastGuildName = lastGuildByPlayer.get(playerUuid);
            String lastPlotType = lastPlotTypeByPlayer.get(playerUuid);

            if (lastGuildName == null && currentGuildName == null) {
                // Still in wilderness, no change
                return;
            } else if (lastGuildName == null && currentGuildName != null) {
                // Entering guild from wilderness
                String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                String message = ChatColor.GREEN + "Entering " + ChatColor.YELLOW + currentGuildName +
                               ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName;
                sendActionBarMessage(player, message);
            } else if (lastGuildName != null && currentGuildName == null) {
                // Leaving guild for wilderness
                sendActionBarMessage(player, ChatColor.GRAY + "Leaving " + ChatColor.YELLOW + lastGuildName + ChatColor.GRAY + " for Wilderness");
            } else if (!lastGuildName.equals(currentGuildName)) {
                // Moving between different guilds
                String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                String message = ChatColor.GREEN + "Entering " + ChatColor.YELLOW + currentGuildName +
                               ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName;
                sendActionBarMessage(player, message);
            } else if (guildBlock.isPresent()) {
                // Same guild - check if plot type changed
                if (lastPlotType == null || !lastPlotType.equals(currentPlotType)) {
                    String plotTypeName = getPlotTypeDisplayName(currentPlotType);
                    sendActionBarMessage(player, ChatColor.YELLOW + currentGuildName + ChatColor.DARK_GRAY + " - " + ChatColor.GREEN + plotTypeName);
                }
                // Don't show ownership info if we just showed plot type
            }

            // Check for plot type changes and dispatch events
            if (lastPlotType != null && !lastPlotType.equals(currentPlotType)) {
                // Player left the previous plot type
                if (guildBlock.isPresent()) {
                    // Get the previous guild block for the leave event
                    Optional<GuildBlock> lastGuildBlockOpt = plotService.getGuildBlock(
                            event.getFrom().getChunk().getX(), event.getFrom().getChunk().getZ(), world);
                    if (lastGuildBlockOpt.isPresent()) {
                        plotTypeHandlerManager.dispatchPlayerLeaveEvent(player, lastGuildBlockOpt.get());
                    }
                }
            }

            if (currentPlotType != null && (lastPlotType == null || !lastPlotType.equals(currentPlotType))) {
                // Player entered a new plot type
                if (guildBlock.isPresent()) {
                    plotTypeHandlerManager.dispatchPlayerEnterEvent(player, guildBlock.get());
                }
            }

            // Update tracking data
            if (currentGuildName != null) {
                lastGuildByPlayer.put(playerUuid, currentGuildName);
            } else {
                // Player is in wilderness, remove or set to null
                lastGuildByPlayer.remove(playerUuid);
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
        lastGuildByPlayer.remove(playerUuid);
        lastPlotTypeByPlayer.remove(playerUuid);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cleanupOfflinePlayer(event.getPlayer().getUniqueId());
    }
}