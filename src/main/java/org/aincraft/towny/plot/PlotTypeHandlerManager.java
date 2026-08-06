package org.aincraft.towny.plot;

import com.google.inject.Singleton;
import org.aincraft.towny.models.TownBlock;

import com.google.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages plot type handler registration and event dispatch
 * Provides thread-safe handler management and event routing
 */
@Singleton
public class PlotTypeHandlerManager {

    private final Map<String, Set<PlotTypeHandler>> typeHandlers = new ConcurrentHashMap<>();
    private final PlotTypeRegistry plotTypeRegistry;
    private final Logger logger;

    @Inject
    public PlotTypeHandlerManager(PlotTypeRegistry plotTypeRegistry, Logger logger) {
        this.plotTypeRegistry = plotTypeRegistry;
        this.logger = logger;
    }

    /**
     * Register a handler for a specific plot type
     */
    public void registerHandler(String plotTypeName, PlotTypeHandler handler) {
        Objects.requireNonNull(plotTypeName, "Plot type name cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        if (!plotTypeRegistry.isPlotTypeRegistered(plotTypeName)) {
            throw new IllegalArgumentException("Plot type '" + plotTypeName + "' is not registered");
        }

        typeHandlers.computeIfAbsent(plotTypeName.toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                  .add(handler);

        logger.info("Registered handler for plot type: " + plotTypeName + " (plugin: " + handler.getPluginName() + ")");
    }


    /**
     * Unregister handlers for a specific plot type
     */
    public boolean unregisterHandler(String plotTypeName) {
        Objects.requireNonNull(plotTypeName, "Plot type name cannot be null");

        Set<PlotTypeHandler> handlers = typeHandlers.remove(plotTypeName.toLowerCase());
        if (handlers != null) {

            logger.info("Unregistered " + handlers.size() + " handlers for plot type: " + plotTypeName);
            return true;
        }
        return false;
    }


    /**
     * Get all handlers for a specific plot type
     */
    public Set<PlotTypeHandler> getHandlersForPlotType(String typeName) {
        Objects.requireNonNull(typeName, "Type name cannot be null");

        Set<PlotTypeHandler> handlers = typeHandlers.get(typeName.toLowerCase());
        return handlers != null ? new HashSet<>(handlers) : Collections.emptySet();
    }

    /**
     * Get all registered handlers
     */
    public Collection<PlotTypeHandler> getAllHandlers() {
        Set<PlotTypeHandler> allHandlers = new HashSet<>();
        for (Set<PlotTypeHandler> handlers : typeHandlers.values()) {
            allHandlers.addAll(handlers);
        }
        return allHandlers;
    }



    /**
     * Dispatch player enter plot event
     */
    public void dispatchPlayerEnterEvent(org.bukkit.entity.Player player, TownBlock plot) {
        String plotTypeName = plot.getPlotType();
        if (plotTypeName == null) {
            return;
        }

        Optional<PlotTypeDefinition> definitionOpt = plotTypeRegistry.getPlotType(plotTypeName);
        if (!definitionOpt.isPresent() || !definitionOpt.get().isEnabled()) {
            return;
        }

        PlotTypeDefinition definition = definitionOpt.get();
        org.aincraft.towny.events.plot.PlotTypeEvent.PlayerEnterPlotEvent event =
            new org.aincraft.towny.events.plot.PlotTypeEvent.PlayerEnterPlotEvent(player, plot, definition);

        Set<PlotTypeHandler> handlers = getHandlersForPlotType(plotTypeName);
        for (PlotTypeHandler handler : handlers) {
            if (handler.isEnabled()) {
                try {
                    handler.onPlayerEnter(event);
                    if (event.isCancelled()) {
                        logger.fine("Player enter event cancelled by handler: " + handler.getPluginName());
                        return;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in plot type handler " + handler.getPluginName(), e);
                }
            }
        }
    }

    /**
     * Dispatch player leave plot event
     */
    public void dispatchPlayerLeaveEvent(org.bukkit.entity.Player player, TownBlock plot) {
        String plotTypeName = plot.getPlotType();
        if (plotTypeName == null) {
            return;
        }

        Optional<PlotTypeDefinition> definitionOpt = plotTypeRegistry.getPlotType(plotTypeName);
        if (!definitionOpt.isPresent() || !definitionOpt.get().isEnabled()) {
            return;
        }

        PlotTypeDefinition definition = definitionOpt.get();
        org.aincraft.towny.events.plot.PlotTypeEvent.PlayerLeavePlotEvent event =
            new org.aincraft.towny.events.plot.PlotTypeEvent.PlayerLeavePlotEvent(player, plot, definition);

        Set<PlotTypeHandler> handlers = getHandlersForPlotType(plotTypeName);
        for (PlotTypeHandler handler : handlers) {
            if (handler.isEnabled()) {
                try {
                    handler.onPlayerLeave(event);
                    if (event.isCancelled()) {
                        logger.fine("Player leave event cancelled by handler: " + handler.getPluginName());
                        return;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in plot type handler " + handler.getPluginName(), e);
                }
            }
        }
    }


    /**
     * Dispatch plot type change event
     */
    public void dispatchPlotTypeChangeEvent(org.bukkit.entity.Player player, TownBlock plot, String oldType, String newType) {
        Optional<PlotTypeDefinition> definitionOpt = plotTypeRegistry.getPlotType(newType);
        if (!definitionOpt.isPresent()) {
            return;
        }

        PlotTypeDefinition definition = definitionOpt.get();
        org.aincraft.towny.events.plot.PlotTypeEvent.PlotTypeChangeEvent event =
            new org.aincraft.towny.events.plot.PlotTypeEvent.PlotTypeChangeEvent(player, plot, definition, oldType, newType);

        Set<PlotTypeHandler> handlers = getHandlersForPlotType(newType);
        for (PlotTypeHandler handler : handlers) {
            if (handler.isEnabled()) {
                try {
                    handler.onPlotTypeChange(event);
                    if (event.isCancelled()) {
                        logger.fine("Plot type change event cancelled by handler: " + handler.getPluginName());
                        return;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in plot type handler " + handler.getPluginName(), e);
                }
            }
        }
    }

}