package org.aincraft.guilds.listeners;


import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.territory.listener.TerritoryTransitionTitleFormatter;
import org.aincraft.guilds.territory.model.LookupResult;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
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
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener for player movement events to handle guild boundary notifications.
 */
public class PlayerMovementListener implements Listener {
    private static final Logger FALLBACK_LOGGER = Logger.getLogger(PlayerMovementListener.class.getName());

    private final JavaPlugin plugin;
    private final PlotService plotService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final PlotTypeHandlerManager plotTypeHandlerManager;
    private final PlotTypeRegistry plotTypeRegistry;
    private final TerritoryRegistry territoryRegistry;
    private final TravelCurrencyService travelCurrencyService;
    private final TravelCurrencyConfig travelCurrencyConfig;

    private final Map<UUID, String> lastGuildByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPlotTypeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, TerritoryLocation> lastTerritoryByPlayer = new ConcurrentHashMap<>();

    public PlayerMovementListener(JavaPlugin plugin, PlotService plotService, GuildService guildService,
                                  ResidentService residentService, PlotTypeHandlerManager plotTypeHandlerManager,
                                  PlotTypeRegistry plotTypeRegistry, TerritoryRegistry territoryRegistry) {
        this(plugin, plotService, guildService, residentService, plotTypeHandlerManager,
                plotTypeRegistry, territoryRegistry, null, null);
    }

    public PlayerMovementListener(JavaPlugin plugin, PlotService plotService, GuildService guildService,
                                  ResidentService residentService, PlotTypeHandlerManager plotTypeHandlerManager,
                                  PlotTypeRegistry plotTypeRegistry, TerritoryRegistry territoryRegistry,
                                  TravelCurrencyService travelCurrencyService,
                                  TravelCurrencyConfig travelCurrencyConfig) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.plotTypeHandlerManager = plotTypeHandlerManager;
        this.plotTypeRegistry = plotTypeRegistry;
        this.territoryRegistry = territoryRegistry;
        this.travelCurrencyService = travelCurrencyService;
        this.travelCurrencyConfig = travelCurrencyConfig;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        if (playerUuid == null) {
            return;
        }

        try {
            updateTerritoryTitle(player, event);
            // Guild/plot processing remains chunk-based.
            if (event.getFrom().getChunk() == event.getTo().getChunk()) {
                return;
            }

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
                        plotInfo = ChatColor.GREEN + "Guild Plot";
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
    private void updateTerritoryTitle(Player player, PlayerMoveEvent event) {
        UUID playerUuid = player.getUniqueId();
        TerritoryLocation previous = lastTerritoryByPlayer.get(playerUuid);
        boolean firstObservation = previous == null;
        if (firstObservation) {
            previous = resolveTerritory(event.getFrom().getWorld().getName(),
                    event.getFrom().getBlockX(), event.getFrom().getBlockZ());
        }
        String toWorld = event.getTo().getWorld().getName();
        TerritoryLocation current = resolveTerritory(toWorld, event.getTo().getBlockX(), event.getTo().getBlockZ());
        if (current.equals(previous)) {
            lastTerritoryByPlayer.put(playerUuid, current);
            return;
        }
        lastTerritoryByPlayer.put(playerUuid, current);
        if (current.contained()
                && (!previous.contained() || !current.territoryId().equals(previous.territoryId()))) {
            awardExplorationMilestone(playerUuid, current.territoryId());
        }
        if (!firstObservation) {
            TerritoryTransitionTitleFormatter.Title title = current.contained()
                    ? TerritoryTransitionTitleFormatter.enter(Optional.of(current.territoryName()), current.zoneType())
                    : TerritoryTransitionTitleFormatter.leave();
            sendTitle(player, title);
        }
    }

    private void awardExplorationMilestone(UUID playerUuid, String territoryId) {
        if (travelCurrencyService == null || travelCurrencyConfig == null) {
            return;
        }
        String eventId = "territory:" + territoryId + ":" + playerUuid;
        long rewardAmount = travelCurrencyConfig.rewardAmount(
                TravelCurrencyRewardSource.EXPLORATION_MILESTONE);
        try {
            CompletionStage<TravelCurrencyService.RewardResult> reward =
                    travelCurrencyService.award(
                            playerUuid,
                            TravelCurrencyRewardSource.EXPLORATION_MILESTONE,
                            eventId,
                            rewardAmount,
                            System.currentTimeMillis());
            observeReward(playerUuid, TravelCurrencyRewardSource.EXPLORATION_MILESTONE,
                    eventId, reward);
        } catch (RuntimeException exception) {
            logRewardFailure(playerUuid, TravelCurrencyRewardSource.EXPLORATION_MILESTONE,
                    eventId, "award invocation threw", exception);
        }
    }

    private void observeReward(UUID actor, TravelCurrencyRewardSource source, String eventId,
                               CompletionStage<TravelCurrencyService.RewardResult> reward) {
        if (reward == null) {
            logRewardFailure(actor, source, eventId, "status=NULL_STAGE", null);
            return;
        }
        try {
            reward.handle((result, error) -> {
                if (error != null) {
                    logRewardFailure(actor, source, eventId, "completion failed", error);
                } else if (result == null) {
                    logRewardFailure(actor, source, eventId, "status=NULL_RESULT", null);
                } else if (result.status() != TravelCurrencyService.RewardStatus.AWARDED
                        && result.status() != TravelCurrencyService.RewardStatus.DUPLICATE) {
                    logRewardFailure(actor, source, eventId, "status=" + result.status(), null);
                }
                return null;
            });
        } catch (RuntimeException exception) {
            logRewardFailure(actor, source, eventId, "completion observation threw", exception);
        }
    }

    private void logRewardFailure(UUID actor, TravelCurrencyRewardSource source, String eventId,
                                  String detail, Throwable error) {
        String message = "Travel currency reward failed source=" + source
                + " eventId=" + eventId + " actor=" + actor + " " + detail;
        Logger logger = plugin == null ? FALLBACK_LOGGER : plugin.getLogger();
        if (logger == null) {
            logger = FALLBACK_LOGGER;
        }
        if (error == null) {
            logger.warning(message);
        } else {
            logger.log(Level.WARNING, message, error);
        }
    }

    private TerritoryLocation resolveTerritory(String world, int blockX, int blockZ) {
        LookupResult result = territoryRegistry.resolve(world, blockX, blockZ);
        return result.isContained()
                ? new TerritoryLocation(
                        result.territoryId().orElseThrow(),
                        result.territory().orElseThrow().name(),
                        result.zoneType().orElse(null))
                : TerritoryLocation.OUTSIDE;
    }

    private void sendTitle(Player player, TerritoryTransitionTitleFormatter.Title title) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle(title.title(), title.subtitle(), 10, 60, 10);
            }
        }.runTask(plugin);
    }

    private record TerritoryLocation(String territoryId, String territoryName,
                                     org.aincraft.guilds.territory.model.ZoneType zoneType) {
        private static final TerritoryLocation OUTSIDE = new TerritoryLocation(null, null, null);

        private boolean contained() {
            return territoryId != null;
        }
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
        lastTerritoryByPlayer.remove(playerUuid);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cleanupOfflinePlayer(event.getPlayer().getUniqueId());
    }
}