package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.aincraft.towny.services.ChatService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Listener for handling town chat functionality
 */
public class TownChatListener implements Listener {

    @Inject
    private ChatService chatService;
    
    @Inject
    private TownService townService;
    
    @Inject
    private ResidentService residentService;

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
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);

        if (townName == null) {
            return;
        }

        // Get the town object
        org.aincraft.towny.models.Town town = townService.getTown(townName).orElse(null);
        if (town == null) {
            return;
        }

        // Cancel original event
        event.setCancelled(true);
        
        // Send town chat (run on main thread to handle Bukkit calls safely)
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(
            org.aincraft.towny.TownyPlugin.getPlugin().asPlugin(),
            () -> chatService.sendTownChat(town.getId(), player, message)
        );
    }
}