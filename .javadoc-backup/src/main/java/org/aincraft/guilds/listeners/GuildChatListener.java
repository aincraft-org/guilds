package org.aincraft.guilds.listeners;

import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
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

    private final JavaPlugin plugin;
    private final ChatService chatService;
    private final GuildService guildService;
    private final ResidentService residentService;

    public GuildChatListener(JavaPlugin plugin, ChatService chatService, GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

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
                .map(org.aincraft.guilds.models.Resident::getGuild)
                .orElse(null);

        if (guildName == null) {
            return;
        }

        // Get the guild object
        org.aincraft.guilds.models.Guild guild = guildService.getGuild(guildName).orElse(null);
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