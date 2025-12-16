package org.aincraft.subregion.events;

import org.aincraft.subregion.Subregion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Event fired when a player exits a subregion.
 */
public class PlayerExitSubregionEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Subregion subregion;
    private final Location from;
    private final Location to;

    public PlayerExitSubregionEvent(Player player, Subregion subregion, Location from, Location to) {
        super(player);
        this.subregion = subregion;
        this.from = from;
        this.to = to;
    }

    /**
     * Gets the subregion being exited.
     */
    public Subregion getSubregion() {
        return subregion;
    }

    /**
     * Gets the location the player is coming from (inside the subregion).
     */
    public Location getFrom() {
        return from;
    }

    /**
     * Gets the location the player is moving to.
     */
    public Location getTo() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
