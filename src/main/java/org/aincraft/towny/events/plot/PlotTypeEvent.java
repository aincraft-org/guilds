package org.aincraft.towny.events.plot;

import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.plot.PlotTypeDefinition;
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
     * Event fired when a player performs an action within a plot
     */
    public static class PlotActionEvent extends PlotTypeEvent {
        private final org.bukkit.entity.Player player;
        private final String action;
        private final Object actionData;

        public PlotActionEvent(org.bukkit.entity.Player player, TownBlock plot, PlotTypeDefinition plotType, String action, Object actionData) {
            super(plot, plotType);
            this.player = player;
            this.action = action;
            this.actionData = actionData;
        }

        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        public String getAction() {
            return action;
        }

        public Object getActionData() {
            return actionData;
        }

        @SuppressWarnings("unchecked")
        public <T> T getActionData(Class<T> type) {
            if (actionData != null && type.isInstance(actionData)) {
                return (T) actionData;
            }
            return null;
        }

        @Override
        public String toString() {
            return "PlotActionEvent{player=" + player.getName() +
                   ", action=" + action +
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

    /**
     * Event fired when a plot type handler is registered
     */
    public static class HandlerRegisteredEvent extends PlotTypeEvent {
        private final String handlerPluginName;
        private final String plotTypeName;

        public HandlerRegisteredEvent(String handlerPluginName, String plotTypeName, PlotTypeDefinition plotType) {
            super(null, plotType);
            this.handlerPluginName = handlerPluginName;
            this.plotTypeName = plotTypeName;
        }

        public String getHandlerPluginName() {
            return handlerPluginName;
        }

        public String getPlotTypeName() {
            return plotTypeName;
        }

        @Override
        public String toString() {
            return "HandlerRegisteredEvent{plugin=" + handlerPluginName +
                   ", plotType=" + plotTypeName +
                   ", definition=" + getPlotType().getDisplayName() + "}";
        }
    }

    /**
     * Event fired when a plot type handler is unregistered
     */
    public static class HandlerUnregisteredEvent extends PlotTypeEvent {
        private final String handlerPluginName;
        private final String plotTypeName;

        public HandlerUnregisteredEvent(String handlerPluginName, String plotTypeName, PlotTypeDefinition plotType) {
            super(null, plotType);
            this.handlerPluginName = handlerPluginName;
            this.plotTypeName = plotTypeName;
        }

        public String getHandlerPluginName() {
            return handlerPluginName;
        }

        public String getPlotTypeName() {
            return plotTypeName;
        }

        @Override
        public String toString() {
            return "HandlerUnregisteredEvent{plugin=" + handlerPluginName +
                   ", plotType=" + plotTypeName +
                   ", definition=" + getPlotType().getDisplayName() + "}";
        }
    }
}