package org.aincraft.guilds.territory.building;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class FastTravelListener implements Listener {
    private final FastTravelService travel;

    public FastTravelListener(FastTravelService travel) {
        this.travel = travel;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            travel.cancel(event.getPlayer().getUniqueId(), FastTravelService.CancelReason.MOVED);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            travel.cancel(player.getUniqueId(), FastTravelService.CancelReason.DAMAGED);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        travel.cancel(event.getEntity().getUniqueId(), FastTravelService.CancelReason.DIED);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        travel.cancel(event.getPlayer().getUniqueId(), FastTravelService.CancelReason.QUIT);
    }
}
