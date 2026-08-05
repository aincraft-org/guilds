package org.aincraft.towny.services;

import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeHandler;
import org.aincraft.towny.plot.PlotTypeHandlerManager;
import org.aincraft.towny.plot.PlotTypeRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for plot type management and handler operations
 * Provides a clean API for managing extensible plot types
 */
public interface PlotTypeService {

    // Plot Type Registry Operations

    /**
     * Register a new plot type
     */
    boolean registerPlotType(String pluginName, PlotTypeDefinition definition);

    /**
     * Unregister a plot type
     */
    boolean unregisterPlotType(String typeName);

    /**
     * Get a plot type by name
     */
    Optional<PlotTypeDefinition> getPlotType(String typeName);

    /**
     * Get all registered plot types
     */
    Collection<PlotTypeDefinition> getAllPlotTypes();

    /**
     * Get plot types registered by a specific plugin
     */
    Collection<PlotTypeDefinition> getPlotTypesByPlugin(String pluginName);

    /**
     * Check if a plot type is registered
     */
    boolean isPlotTypeRegistered(String typeName);

    /**
     * Enable or disable a plot type
     */
    boolean setPlotTypeEnabled(String typeName, boolean enabled);

    // Handler Management Operations

    /**
     * Register a handler for a specific plot type
     */
    boolean registerHandler(String pluginName, String plotTypeName, PlotTypeHandler handler);

    /**
     * Unregister handlers for a specific plot type
     */
    boolean unregisterHandler(String plotTypeName);

    /**
     * Unregister all handlers for a plugin
     */
    int unregisterPluginHandlers(String pluginName);

    /**
     * Get handlers for a plot type
     */
    List<PlotTypeHandler> getHandlersForPlotType(String plotTypeName);

    /**
     * Get all registered handlers
     */
    List<PlotTypeHandler> getAllHandlers();

    // Town Block Integration

    /**
     * Change the plot type of a town block
     */
    boolean changePlotType(UUID townBlockId, String newPlotType);

    /**
     * Get the plot type of a town block
     */
    Optional<String> getPlotType(UUID townBlockId);

    /**
     * Get plot type definition for a town block
     */
    Optional<PlotTypeDefinition> getPlotTypeDefinition(UUID townBlockId);

    // Statistics and Information

    /**
     * Get registry statistics
     */
    PlotTypeRegistry.RegistryStats getRegistryStats();

    /**
     * Get handler statistics
     */
    PlotTypeHandlerManager.HandlerStats getHandlerStats();

    /**
     * Get combined statistics
     */
    PlotTypeStats getStats();

    /**
     * Combined statistics for plot type system
     */
    class PlotTypeStats {
        private final PlotTypeRegistry.RegistryStats registryStats;
        private final PlotTypeHandlerManager.HandlerStats handlerStats;

        public PlotTypeStats(PlotTypeRegistry.RegistryStats registryStats,
                           PlotTypeHandlerManager.HandlerStats handlerStats) {
            this.registryStats = registryStats;
            this.handlerStats = handlerStats;
        }

        public PlotTypeRegistry.RegistryStats getRegistryStats() {
            return registryStats;
        }

        public PlotTypeHandlerManager.HandlerStats getHandlerStats() {
            return handlerStats;
        }

        @Override
        public String toString() {
            return "PlotTypeStats{" +
                    "registry=" + registryStats +
                    ", handlers=" + handlerStats +
                    '}';
        }
    }

    // Plugin Lifecycle

    /**
     * Initialize built-in plot types
     */
    void initializeBuiltInTypes();

    /**
     * Clean up plugin data
     */
    int cleanupPlugin(String pluginName);

    /**
     * Get all registered plugins
     */
    List<String> getRegisteredPlugins();
}