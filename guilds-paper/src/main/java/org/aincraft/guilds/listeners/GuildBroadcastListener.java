package org.aincraft.guilds.listeners;


import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.BroadcastMessage;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.BroadcastService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.utils.BroadcastFormatter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Listener for guild broadcasting system events
 * Handles automatic broadcast creation and delivery
 */
public class GuildBroadcastListener implements Listener {

    private final JavaPlugin plugin;
    private final BroadcastService broadcastService;
    private final ResidentService residentService;
    private final GuildService guildService;
    private final Logger logger;


    public GuildBroadcastListener(JavaPlugin plugin, BroadcastService broadcastService,
                               ResidentService residentService, GuildService guildService, Logger logger) {
        this.plugin = plugin;
        this.broadcastService = broadcastService;
        this.residentService = residentService;
        this.guildService = guildService;
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
        // Check if player is in a guild
        residentService.getResident(playerUuid).ifPresent(resident -> {
            if (!resident.hasGuild()) {
                return; // Player is not in a guild
            }

            String guildName = resident.getGuild();
            guildService.getGuild(guildName).ifPresent(guild -> {
                String guildId = guild.getId();

                // Get broadcasts for this player
                List<BroadcastMessage> broadcasts = broadcastService.getBroadcastsForPlayer(
                    guildId, playerUuid, getPlayerRole(playerUuid, guild)
                );

                if (!broadcasts.isEmpty()) {
                    // Send broadcasts to player after a short delay to let them see the join message
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        sendBroadcastsToPlayer(player, broadcasts);
                    }, 60L); // 3 seconds delay (60 ticks)
                }

                // Check if this is a new resident and create welcome message if needed
                if (shouldCreateWelcomeMessage(resident)) {
                    createWelcomeBroadcast(guildId, player.getName());
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
        player.sendMessage(ChatColor.YELLOW + "=== Guild Broadcasts ===");

        // Sort broadcasts by priority (highest first)
        broadcasts.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (BroadcastMessage broadcast : broadcasts) {
            player.sendMessage(BroadcastFormatter.format(broadcast));
        }

        player.sendMessage(ChatColor.YELLOW + "========================");
    }

    /**
     * Get player's role in the guild
     */
    private String getPlayerRole(UUID playerUuid, Guild guild) {
        if (guild.getMayorUuid().equals(playerUuid)) {
            return "mayor";
        } else if (guild.getAssistants().contains(playerUuid)) {
            return "assistant";
        } else {
            return "resident";
        }
    }

    /**
     * Check if a welcome message should be created for this resident
     */
    private boolean shouldCreateWelcomeMessage(Resident resident) {
        // Check if resident is new (has been guild member for less than 5 minutes)
        return resident.getJoinedAt() != null &&
               System.currentTimeMillis() - resident.getJoinedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() < 300000; // 5 minutes
    }

    /**
     * Create a welcome broadcast for a new resident
     */
    private void createWelcomeBroadcast(String guildId, String playerName) {
        try {
            BroadcastMessage welcome = broadcastService.createWelcomeMessage(guildId, playerName);
            welcome.setTitle("Welcome " + playerName + "!");
            welcome.setContent("Please give a warm welcome to our newest resident! " +
                             playerName + " has just joined our guild. Feel free to help them get settled in.");
            welcome.setTargetAudience(BroadcastMessage.Audience.ALL);
            welcome.setExpirationInHours(24); // Welcome messages expire after 24 hours

            broadcastService.updateBroadcast(welcome);

            // Send the welcome message to all online guild members
            int sentCount = broadcastService.sendBroadcastToOnlineMembers(welcome);
            logger.info("Created welcome broadcast for " + playerName + " in guild " + guildId +
                       " (sent to " + sentCount + " players)");

        } catch (Exception e) {
            logger.warning("Failed to create welcome broadcast for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Create an automatic alert when a guild reaches a new level
     */
    public void createGuildLevelUpAlert(Guild guild, int newLevel) {
        if (guild == null) {
            return;
        }

        try {
            String title = "Guild Level Up! 🎉";
            String content = String.format("Congratulations! %s has reached level %d! " +
                                          "New benefits and features have been unlocked.",
                                          guild.getName(), newLevel);

            BroadcastMessage alert = broadcastService.createAlertMessage(
                guild.getId(), title, content,
                guild.getMayorUuid(), "Guild System",
                BroadcastMessage.Priority.HIGH
            );

            alert.setTargetAudience(BroadcastMessage.Audience.ALL);
            alert.setExpirationInDays(3); // Level up messages last 3 days

            broadcastService.updateBroadcast(alert);
            broadcastService.sendBroadcastToOnlineMembers(alert);

            logger.info("Created level up alert for guild " + guild.getName() + " level " + newLevel);

        } catch (Exception e) {
            logger.warning("Failed to create level up alert for guild " + guild.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Create an economic alert for the guild
     */
    public void createEconomicAlert(Guild guild, String title, String content, int priority) {
        if (guild == null) {
            return;
        }

        try {
            BroadcastMessage economicAlert = broadcastService.createBroadcast(
                guild.getId(), BroadcastMessage.Type.ECONOMIC, title, content,
                guild.getMayorUuid(), "Economy System"
            );

            economicAlert.setPriority(priority);
            economicAlert.setTargetAudience(BroadcastMessage.Audience.ASSISTANTS); // Economic alerts go to leadership
            economicAlert.setExpirationInHours(12);

            broadcastService.updateBroadcast(economicAlert);
            broadcastService.sendBroadcastToOnlineMembers(economicAlert);

            logger.info("Created economic alert for guild " + guild.getName() + ": " + title);

        } catch (Exception e) {
            logger.warning("Failed to create economic alert for guild " + guild.getName() + ": " + e.getMessage());
        }
    }
}