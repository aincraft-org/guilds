package dev.mintychochip.guilds.listeners;

import dev.mintychochip.guilds.services.GuildHearthstoneService;
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

    /** The hearthstone service. */
    private final GuildHearthstoneService hearthstoneService;
    /** The hearthstone material. */
    private final Material hearthstoneMaterial;

    /**
     * Creates a new guild hearthstone listener instance.
     * @param hearthstoneService the hearthstone service
     * @param hearthstoneMaterial the hearthstone material
     */
    public GuildHearthstoneListener(GuildHearthstoneService hearthstoneService, Material hearthstoneMaterial) {
        this.hearthstoneService = hearthstoneService;
        this.hearthstoneMaterial = hearthstoneMaterial;
    }

    /**
     * Handles the player interact.
     * @param event the event
     */
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
