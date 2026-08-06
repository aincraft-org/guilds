package org.aincraft.guilds.events.plot;

import org.aincraft.guilds.models.TownBlock;
import org.aincraft.guilds.plot.PlotTypeDefinition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base event for plot type related events
 * Extends Bukkit's Event system for proper event handling
 */
public abstract class PlotTypeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final TownBlock plot;
    private final PlotTypeDefinition plotType;
    private boolean cancelled = false;
    private String cancelReason = null;

    public PlotTypeEvent(TownBlock plot, PlotTypeDefinition plotType) {
        this.plot = plot;
        this.plotType = plotType;
    }

    public TownBlock getPlot() {
        return plot;
    }

    public PlotTypeDefinition getPlotType() {
        return plotType;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelled(boolean cancelled, String reason) {
        this.cancelled = cancelled;
        this.cancelReason = reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Event fired when a player enters a plot of a specific type
     */
    public static class PlayerEnterPlotEvent extends PlotTypeEvent {
        private final org.bukkit.entity.Player player;

        public PlayerEnterPlotEvent(org.bukkit.entity.Player player, TownBlock plot, PlotTypeDefinition plotType) {
            super(plot, plotType);
            this.player = player;
        }

        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        @Override
        public String toString() {
            return "PlayerEnterPlotEvent{player=" + player.getName() +
                   ", plotType=" + getPlotType().getTypeName() +
                   ", location=" + getPlot().getX() + "," + getPlot().getZ() + "}";
        }
    }

    /**
     * Event fired when a player leaves a plot of a specific type
     */
    public static class PlayerLeavePlotEvent extends PlotTypeEvent {
        private final org.bukkit.entity.Player player;

        public PlayerLeavePlotEvent(org.bukkit.entity.Player player, TownBlock plot, PlotTypeDefinition plotType) {
            super(plot, plotType);
            this.player = player;
        }

        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        @Override
        public String toString() {
            return "PlayerLeavePlotEvent{player=" + player.getName() +
                   ", plotType=" + getPlotType().getTypeName() +
                   ", location=" + getPlot().getX() + "," + getPlot().getZ() + "}";
        }
    }


    /**
     * Event fired when a plot type is about to change
     */
    public static class PlotTypeChangeEvent extends PlotTypeEvent {
        private final org.bukkit.entity.Player player;
        private final String oldType;
        private final String newType;

        public PlotTypeChangeEvent(org.bukkit.entity.Player player, TownBlock plot, PlotTypeDefinition plotType, String oldType, String newType) {
            super(plot, plotType);
            this.player = player;
            this.oldType = oldType;
            this.newType = newType;
        }

        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        public String getOldType() {
            return oldType;
        }

        public String getNewType() {
            return newType;
        }

        @Override
        public String toString() {
            return "PlotTypeChangeEvent{player=" + player.getName() +
                   ", oldType=" + oldType +
                   ", newType=" + newType +
                   ", location=" + getPlot().getX() + "," + getPlot().getZ() + "}";
        }
    }

}