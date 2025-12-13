package org.aincraft.towny.plot;

import org.aincraft.towny.events.plot.PlotTypeEvent;

/**
 * Interface for plugins to handle plot type-specific events
 * Allows external plugins to respond to player actions within specific plot types
 */
public interface PlotTypeHandler {

    /**
     * Called when a player enters a plot of the registered type
     * @param event The player enter plot event
     */
    void onPlayerEnter(PlotTypeEvent.PlayerEnterPlotEvent event);

    /**
     * Called when a player leaves a plot of the registered type
     * @param event The player leave plot event
     */
    void onPlayerLeave(PlotTypeEvent.PlayerLeavePlotEvent event);

    /**
     * Called when a player performs an action within a plot of the registered type
     * @param event The plot action event
     */
    void onPlotAction(PlotTypeEvent.PlotActionEvent event);

    /**
     * Called when the plot type is about to change
     * @param event The plot type change event
     */
    void onPlotTypeChange(PlotTypeEvent.PlotTypeChangeEvent event);

    /**
     * Get the name of the plugin that owns this handler
     * Used for tracking and debugging purposes
     */
    String getPluginName();

    /**
     * Get the plot type names this handler is registered for
     * @return Array of plot type names
     */
    String[] getHandledPlotTypes();

    /**
     * Check if this handler is enabled
     * @return true if the handler should receive events
     */
    default boolean isEnabled() {
        return true;
    }
}