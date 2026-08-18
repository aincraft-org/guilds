package dev.mintychochip.guilds.listeners;

import dev.mintychochip.guilds.services.ChatService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for handling guild chat functionality
 */
public class GuildChatListener implements Listener {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The chat service. */
    private final ChatService chatService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;

    /**
     * Creates a new guild chat listener instance.
     * @param plugin the plugin
     * @param chatService the chat service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public GuildChatListener(JavaPlugin plugin, ChatService chatService, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Handles the player chat.
     * @param event the event
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player has guild chat enabled
        if (!chatService.isGuildChatEnabled(player.getUniqueId())) {
            return;
        }

        // Check if player is in a guild
        String guildName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(dev.mintychochip.guilds.models.Resident::getGuild)
                .orElse(null);

        if (guildName == null) {
            return;
        }

        // Get the guild object
        dev.mintychochip.guilds.models.Guild guild = guildService.getGuild(guildName).orElse(null);
        if (guild == null) {
            return;
        }

        // Cancel original event
        event.setCancelled(true);

        // Send guild chat (run on main thread to handle Bukkit calls safely)
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> chatService.sendGuildChat(guild.getId(), player, message)
        );
    }
}