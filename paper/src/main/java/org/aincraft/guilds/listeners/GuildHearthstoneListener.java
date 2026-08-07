package org.aincraft.guilds.listeners;

import org.aincraft.guilds.services.GuildHearthstoneService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Intercepts right-click with the configured hearthstone item and delegates
 * to {@link GuildHearthstoneService}.
 */
public class GuildHearthstoneListener implements Listener {

    private final GuildHearthstoneService hearthstoneService;
    private final Material hearthstoneMaterial;

    public GuildHearthstoneListener(GuildHearthstoneService hearthstoneService, Material hearthstoneMaterial) {
        this.hearthstoneService = hearthstoneService;
        this.hearthstoneMaterial = hearthstoneMaterial;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != hearthstoneMaterial) return;
        Player player = event.getPlayer();
        boolean teleported = hearthstoneService.teleportToGuildSpawn(player.getUniqueId());
        if (teleported) {
            event.setCancelled(true);
        }
    }
}
