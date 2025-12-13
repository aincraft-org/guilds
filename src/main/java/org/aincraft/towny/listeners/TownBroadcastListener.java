package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.BroadcastMessage;
import org.aincraft.towny.models.Resident;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.BroadcastService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Listener for town broadcasting system events
 * Handles automatic broadcast creation and delivery
 */
public class TownBroadcastListener implements Listener {

    private final TownyPlugin plugin;
    private final BroadcastService broadcastService;
    private final ResidentService residentService;
    private final TownService townService;
    private final Logger logger;

    @Inject
    public TownBroadcastListener(TownyPlugin plugin, BroadcastService broadcastService,
                               ResidentService residentService, TownService townService, Logger logger) {
        this.plugin = plugin;
        this.broadcastService = broadcastService;
        this.residentService = residentService;
        this.townService = townService;
        this.logger = logger;
    }

    /**
     * Handle player join - show welcome broadcasts and deliver pending messages
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Process broadcasts asynchronously to avoid blocking the main thread
        CompletableFuture.runAsync(() -> {
            try {
                processPlayerJoin(player, playerUuid);
            } catch (Exception e) {
                logger.warning("Error processing broadcasts for player " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Handle player quit - cleanup any player-specific broadcast data
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup could be done here if needed
        // For now, just log the quit event for debugging
        logger.fine("Player " + event.getPlayer().getName() + " quit - broadcast cleanup completed");
    }

    /**
     * Process player join for broadcasting
     */
    private void processPlayerJoin(Player player, UUID playerUuid) {
        // Check if player is in a town
        residentService.getResident(playerUuid).ifPresent(resident -> {
            if (!resident.hasTown()) {
                return; // Player is not in a town
            }

            String townName = resident.getTown();
            townService.getTown(townName).ifPresent(town -> {
                String townId = town.getId();

                // Get broadcasts for this player
                List<BroadcastMessage> broadcasts = broadcastService.getBroadcastsForPlayer(
                    townId, playerUuid, getPlayerRole(playerUuid, town)
                );

                if (!broadcasts.isEmpty()) {
                    // Send broadcasts to player after a short delay to let them see the join message
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        sendBroadcastsToPlayer(player, broadcasts);
                    }, 60L); // 3 seconds delay (60 ticks)
                }

                // Check if this is a new resident and create welcome message if needed
                if (shouldCreateWelcomeMessage(resident)) {
                    createWelcomeBroadcast(townId, player.getName());
                }
            });
        });
    }

    /**
     * Send broadcasts to a player with proper formatting
     */
    private void sendBroadcastsToPlayer(Player player, List<BroadcastMessage> broadcasts) {
        if (broadcasts.isEmpty()) {
            return;
        }

        // Send header
        player.sendMessage(ChatColor.YELLOW + "=== Town Broadcasts ===");

        // Sort broadcasts by priority (highest first)
        broadcasts.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (BroadcastMessage broadcast : broadcasts) {
            String formattedMessage = formatBroadcastForPlayer(broadcast);
            player.sendMessage(formattedMessage);
        }

        player.sendMessage(ChatColor.YELLOW + "========================");
    }

    /**
     * Format a broadcast message for display to a player
     */
    private String formatBroadcastForPlayer(BroadcastMessage broadcast) {
        StringBuilder message = new StringBuilder();

        // Add header based on message type with priority color
        String typeColor = getTypeColor(broadcast.getPriority());
        String priorityIndicator = getPriorityIndicator(broadcast.getPriority());

        switch (broadcast.getMessageType()) {
            case BroadcastMessage.Type.ALERT:
                message.append(typeColor).append("[§cALERT").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.ANNOUNCEMENT:
                message.append(typeColor).append("[§eANNOUNCE").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.WELCOME:
                message.append(typeColor).append("[§aWELCOME").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.WARNING:
                message.append(typeColor).append("[§4WARNING").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.CELEBRATION:
                message.append(typeColor).append("[§6CELEBRATE").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.ECONOMIC:
                message.append(typeColor).append("[§2ECONOMY").append(typeColor).append("] ");
                break;
            default:
                message.append(typeColor).append("[§fBROADCAST").append(typeColor).append("] ");
        }

        // Add priority indicator if high priority
        if (broadcast.getPriority() >= BroadcastMessage.Priority.HIGH) {
            message.append(priorityIndicator).append(" ");
        }

        // Add title and content
        message.append("§f").append(broadcast.getTitle()).append("\n");
        message.append("§7").append(broadcast.getContent()).append("\n");

        // Add footer with sender info
        message.append("§8- ").append(broadcast.getSenderName())
               .append(" §8(").append(broadcast.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")))
               .append("§8)");

        return message.toString();
    }

    /**
     * Get color based on broadcast priority
     */
    private String getTypeColor(int priority) {
        switch (priority) {
            case BroadcastMessage.Priority.CRITICAL:
                return "§4";
            case BroadcastMessage.Priority.URGENT:
                return "§c";
            case BroadcastMessage.Priority.HIGH:
                return "§6";
            case BroadcastMessage.Priority.NORMAL:
                return "§e";
            case BroadcastMessage.Priority.LOW:
                return "§a";
            default:
                return "§f";
        }
    }

    /**
     * Get priority indicator symbol
     */
    private String getPriorityIndicator(int priority) {
        switch (priority) {
            case BroadcastMessage.Priority.CRITICAL:
                return "‼";
            case BroadcastMessage.Priority.URGENT:
                return "⚠";
            case BroadcastMessage.Priority.HIGH:
                return "⬆";
            default:
                return "";
        }
    }

    /**
     * Get player's role in the town
     */
    private String getPlayerRole(UUID playerUuid, Town town) {
        if (town.getMayorUuid().equals(playerUuid)) {
            return "mayor";
        } else if (town.getAssistants().contains(playerUuid)) {
            return "assistant";
        } else {
            return "resident";
        }
    }

    /**
     * Check if a welcome message should be created for this resident
     */
    private boolean shouldCreateWelcomeMessage(Resident resident) {
        // Check if resident is new (has been town member for less than 5 minutes)
        return resident.getJoinedAt() != null &&
               System.currentTimeMillis() - resident.getJoinedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() < 300000; // 5 minutes
    }

    /**
     * Create a welcome broadcast for a new resident
     */
    private void createWelcomeBroadcast(String townId, String playerName) {
        try {
            BroadcastMessage welcome = broadcastService.createWelcomeMessage(townId, playerName);
            welcome.setTitle("Welcome " + playerName + "!");
            welcome.setContent("Please give a warm welcome to our newest resident! " +
                             playerName + " has just joined our town. Feel free to help them get settled in.");
            welcome.setTargetAudience(BroadcastMessage.Audience.ALL);
            welcome.setExpirationInHours(24); // Welcome messages expire after 24 hours

            broadcastService.updateBroadcast(welcome);

            // Send the welcome message to all online town members
            int sentCount = broadcastService.sendBroadcastToOnlineMembers(welcome);
            logger.info("Created welcome broadcast for " + playerName + " in town " + townId +
                       " (sent to " + sentCount + " players)");

        } catch (Exception e) {
            logger.warning("Failed to create welcome broadcast for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Create an automatic alert when a town reaches a new level
     */
    public void createTownLevelUpAlert(Town town, int newLevel) {
        if (town == null) {
            return;
        }

        try {
            String title = "Town Level Up! 🎉";
            String content = String.format("Congratulations! %s has reached level %d! " +
                                          "New benefits and features have been unlocked.",
                                          town.getName(), newLevel);

            BroadcastMessage alert = broadcastService.createAlertMessage(
                town.getId(), title, content,
                town.getMayorUuid(), "Town System",
                BroadcastMessage.Priority.HIGH
            );

            alert.setTargetAudience(BroadcastMessage.Audience.ALL);
            alert.setExpirationInDays(3); // Level up messages last 3 days

            broadcastService.updateBroadcast(alert);
            broadcastService.sendBroadcastToOnlineMembers(alert);

            logger.info("Created level up alert for town " + town.getName() + " level " + newLevel);

        } catch (Exception e) {
            logger.warning("Failed to create level up alert for town " + town.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Create an economic alert for the town
     */
    public void createEconomicAlert(Town town, String title, String content, int priority) {
        if (town == null) {
            return;
        }

        try {
            BroadcastMessage economicAlert = broadcastService.createBroadcast(
                town.getId(), BroadcastMessage.Type.ECONOMIC, title, content,
                town.getMayorUuid(), "Economy System"
            );

            economicAlert.setPriority(priority);
            economicAlert.setTargetAudience(BroadcastMessage.Audience.ASSISTANTS); // Economic alerts go to leadership
            economicAlert.setExpirationInHours(12);

            broadcastService.updateBroadcast(economicAlert);
            broadcastService.sendBroadcastToOnlineMembers(economicAlert);

            logger.info("Created economic alert for town " + town.getName() + ": " + title);

        } catch (Exception e) {
            logger.warning("Failed to create economic alert for town " + town.getName() + ": " + e.getMessage());
        }
    }
}