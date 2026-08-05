package org.aincraft.towny.plot;

import com.google.inject.Singleton;
import org.aincraft.towny.models.TownBlock;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.google.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages plot type handler registration and event dispatch
 * Provides thread-safe handler management and event routing
 */
@Singleton
public class PlotTypeHandlerManager {

    private final Map<String, Set<PlotTypeHandler>> typeHandlers = new ConcurrentHashMap<>();
    private final Map<Plugin, Set<String>> pluginHandlers = new ConcurrentHashMap<>();
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
     * Register a handler for a specific plot type with plugin tracking
     */
    public void registerHandler(Plugin plugin, String plotTypeName, PlotTypeHandler handler) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");

        registerHandler(plotTypeName, handler);

        pluginHandlers.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet())
                     .add(plotTypeName.toLowerCase());
    }

    /**
     * Unregister handlers for a specific plot type
     */
    public boolean unregisterHandler(String plotTypeName) {
        Objects.requireNonNull(plotTypeName, "Plot type name cannot be null");

        Set<PlotTypeHandler> handlers = typeHandlers.remove(plotTypeName.toLowerCase());
        if (handlers != null) {
            // Remove from plugin tracking
            for (Set<String> types : pluginHandlers.values()) {
                types.remove(plotTypeName.toLowerCase());
            }

            logger.info("Unregistered " + handlers.size() + " handlers for plot type: " + plotTypeName);
            return true;
        }
        return false;
    }

    /**
     * Unregister handlers for a specific plugin
     */
    public int unregisterHandler(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");

        Set<String> types = pluginHandlers.remove(plugin);
        if (types == null) {
            return 0;
        }

        int removedCount = 0;
        for (String typeName : types) {
            Set<PlotTypeHandler> handlers = typeHandlers.get(typeName);
            if (handlers != null) {
                int beforeSize = handlers.size();
                handlers.removeIf(handler -> handler.getPluginName().equals(plugin.getName()));
                removedCount += (beforeSize - handlers.size());
            }
        }

        logger.info("Unregistered " + removedCount + " handlers for plugin: " + plugin.getName());
        return removedCount;
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
     * Get handlers for a specific plugin
     */
    public Set<PlotTypeHandler> getHandlersForPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");

        Set<PlotTypeHandler> pluginHandlers = new HashSet<>();
        String pluginName = plugin.getName();

        for (Set<PlotTypeHandler> handlers : typeHandlers.values()) {
            for (PlotTypeHandler handler : handlers) {
                if (handler.getPluginName().equals(pluginName)) {
                    pluginHandlers.add(handler);
                }
            }
        }

        return pluginHandlers;
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
                    logger.warning("Error in plot type handler " + handler.getPluginName() + ": " + e.getMessage());
                    e.printStackTrace();
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
                    logger.warning("Error in plot type handler " + handler.getPluginName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Dispatch plot action event
     */
    public void dispatchPlotActionEvent(org.bukkit.entity.Player player, TownBlock plot, String action, Object actionData) {
        String plotTypeName = plot.getPlotType();
        if (plotTypeName == null) {
            return;
        }

        Optional<PlotTypeDefinition> definitionOpt = plotTypeRegistry.getPlotType(plotTypeName);
        if (!definitionOpt.isPresent() || !definitionOpt.get().isEnabled()) {
            return;
        }

        PlotTypeDefinition definition = definitionOpt.get();
        org.aincraft.towny.events.plot.PlotTypeEvent.PlotActionEvent event =
            new org.aincraft.towny.events.plot.PlotTypeEvent.PlotActionEvent(player, plot, definition, action, actionData);

        Set<PlotTypeHandler> handlers = getHandlersForPlotType(plotTypeName);
        for (PlotTypeHandler handler : handlers) {
            if (handler.isEnabled()) {
                try {
                    handler.onPlotAction(event);
                    if (event.isCancelled()) {
                        logger.fine("Plot action event cancelled by handler: " + handler.getPluginName() +
                                  (event.getCancelReason() != null ? " - " + event.getCancelReason() : ""));
                        return;
                    }
                } catch (Exception e) {
                    logger.warning("Error in plot type handler " + handler.getPluginName() + ": " + e.getMessage());
                    e.printStackTrace();
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
                    logger.warning("Error in plot type handler " + handler.getPluginName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get statistics about registered handlers
     */
    public HandlerStats getStats() {
        int totalHandlers = 0;
        int enabledHandlers = 0;
        int pluginCount = pluginHandlers.size();

        for (PlotTypeHandler handler : getAllHandlers()) {
            totalHandlers++;
            if (handler.isEnabled()) {
                enabledHandlers++;
            }
        }

        return new HandlerStats(totalHandlers, enabledHandlers, typeHandlers.size(), pluginCount);
    }

    /**
     * Handler statistics for monitoring
     */
    public static class HandlerStats {
        private final int totalHandlers;
        private final int enabledHandlers;
        private final int typeCount;
        private final int pluginCount;

        public HandlerStats(int totalHandlers, int enabledHandlers, int typeCount, int pluginCount) {
            this.totalHandlers = totalHandlers;
            this.enabledHandlers = enabledHandlers;
            this.typeCount = typeCount;
            this.pluginCount = pluginCount;
        }

        public int getTotalHandlers() { return totalHandlers; }
        public int getEnabledHandlers() { return enabledHandlers; }
        public int getTypeCount() { return typeCount; }
        public int getPluginCount() { return pluginCount; }

        @Override
        public String toString() {
            return String.format("HandlerStats{total=%d, enabled=%d, types=%d, plugins=%d}",
                               totalHandlers, enabledHandlers, typeCount, pluginCount);
        }
    }
}