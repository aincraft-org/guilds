package dev.mintychochip.guilds.events.plot;

import dev.mintychochip.guilds.models.GuildBlock;
import dev.mintychochip.guilds.plot.PlotTypeDefinition;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base event for plot type related events
 * Extends Bukkit's Event system for proper event handling
 */
public abstract class PlotTypeEvent extends Event {
    /** The handlers. */
    private static final HandlerList handlers = new HandlerList();
    /** The plot. */
    private final GuildBlock plot;
    /** The plot type. */
    private final PlotTypeDefinition plotType;
    /** The cancelled. */
    private boolean cancelled = false;
    /** The cancel reason. */
    private String cancelReason = null;

    /**
     * Creates a new plot type event instance.
     * @param plot the plot
     * @param plotType the plot type
     */
    public PlotTypeEvent(GuildBlock plot, PlotTypeDefinition plotType) {
        this.plot = plot;
        this.plotType = plotType;
    }

    /**
     * Returns the plot.
     * @return the result
     */
    public GuildBlock getPlot() {
        return plot;
    }

    /**
     * Returns the plot type.
     * @return the result
     */
    public PlotTypeDefinition getPlotType() {
        return plotType;
    }

    /**
     * Returns whether cancelled.
     * @return the result
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancelled.
     * @param cancelled the cancelled
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Returns the cancel reason.
     * @return the result
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Sets the cancelled.
     * @param cancelled the cancelled
     * @param reason the reason
     */
    public void setCancelled(boolean cancelled, String reason) {
        this.cancelled = cancelled;
        this.cancelReason = reason;
    }

    /**
     * Returns the handlers.
     * @return the result
     */
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Returns the handler list.
     * @return the result
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Event fired when a player enters a plot of a specific type
     */
    public static class PlayerEnterPlotEvent extends PlotTypeEvent {
        /** The player. */
        private final org.bukkit.entity.Player player;

        /**
         * Creates a new player enter plot event instance.
         * @param player the player
         * @param plot the plot
         * @param plotType the plot type
         */
        public PlayerEnterPlotEvent(org.bukkit.entity.Player player, GuildBlock plot, PlotTypeDefinition plotType) {
            super(plot, plotType);
            this.player = player;
        }

        /**
         * Returns the player.
         * @return the result
         */
        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        /** Returns a string representation of this object. */
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
        /** The player. */
        private final org.bukkit.entity.Player player;

        /**
         * Creates a new player leave plot event instance.
         * @param player the player
         * @param plot the plot
         * @param plotType the plot type
         */
        public PlayerLeavePlotEvent(org.bukkit.entity.Player player, GuildBlock plot, PlotTypeDefinition plotType) {
            super(plot, plotType);
            this.player = player;
        }

        /**
         * Returns the player.
         * @return the result
         */
        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        /** Returns a string representation of this object. */
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
        /** The player. */
        private final org.bukkit.entity.Player player;
        /** The old type. */
        private final String oldType;
        /** The new type. */
        private final String newType;

        /**
         * Creates a new plot type change event instance.
         * @param player the player
         * @param plot the plot
         * @param plotType the plot type
         * @param oldType the old type
         * @param newType the new type
         */
        public PlotTypeChangeEvent(org.bukkit.entity.Player player, GuildBlock plot, PlotTypeDefinition plotType, String oldType, String newType) {
            super(plot, plotType);
            this.player = player;
            this.oldType = oldType;
            this.newType = newType;
        }

        /**
         * Returns the player.
         * @return the result
         */
        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        /**
         * Returns the old type.
         * @return the result
         */
        public String getOldType() {
            return oldType;
        }

        /**
         * Returns the new type.
         * @return the result
         */
        public String getNewType() {
            return newType;
        }

        /** Returns a string representation of this object. */
        @Override
        public String toString() {
            return "PlotTypeChangeEvent{player=" + player.getName() +
                   ", oldType=" + oldType +
                   ", newType=" + newType +
                   ", location=" + getPlot().getX() + "," + getPlot().getZ() + "}";
        }
    }

}