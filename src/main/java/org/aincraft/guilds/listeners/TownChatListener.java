package org.aincraft.guilds.listeners;

import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for handling town chat functionality
 */
public class TownChatListener implements Listener {

    private final JavaPlugin plugin;
    private final ChatService chatService;
    private final TownService townService;
    private final ResidentService residentService;

    public TownChatListener(JavaPlugin plugin, ChatService chatService, TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.townService = townService;
        this.residentService = residentService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player has town chat enabled
        if (!chatService.isTownChatEnabled(player.getUniqueId())) {
            return;
        }

        // Check if player is in a town
        String townName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.guilds.models.Resident::getTown)
                .orElse(null);

        if (townName == null) {
            return;
        }

        // Get the town object
        org.aincraft.guilds.models.Town town = townService.getTown(townName).orElse(null);
        if (town == null) {
            return;
        }

        // Cancel original event
        event.setCancelled(true);

        // Send town chat (run on main thread to handle Bukkit calls safely)
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> chatService.sendTownChat(town.getId(), player, message)
        );
    }
}