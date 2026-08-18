package dev.mintychochip.guilds.plot;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for managing plot type definitions and plugin registrations
 * Provides centralized storage and lookup for all plot types in the system
 */
public interface PlotTypeRegistry {

    /**
     * Register a plot type definition
     * @param definition The plot type definition to register
     * @throws IllegalArgumentException if a type with the same name already exists
     */
    void registerPlotType(PlotTypeDefinition definition);

    /**
     * Register a plot type definition with explicit plugin association
     * @param pluginName The name of the plugin registering this type
     * @param definition The plot type definition to register
     * @throws IllegalArgumentException if a type with the same name already exists
     */
    void registerPlotType(String pluginName, PlotTypeDefinition definition);

    /**
     * Unregister a plot type
     * @param typeName The type name to unregister
     * @return true if the type was found and removed, false otherwise
     */
    boolean unregisterPlotType(String typeName);

    /**
     * Get a plot type definition by name
     * @param typeName The type name to look up
     * @return Optional containing the definition if found
     */
    Optional<PlotTypeDefinition> getPlotType(String typeName);

    /**
     * Get all registered plot type definitions
     * @return Collection of all plot type definitions
     */
    Collection<PlotTypeDefinition> getAllPlotTypes();

    /**
     * Get all plot types registered by a specific plugin
     * @param pluginName The plugin name to filter by
     * @return Collection of plot types from the specified plugin
     */
    Collection<PlotTypeDefinition> getPlotTypesByPlugin(String pluginName);

    /**
     * Get all built-in plot types (not registered by plugins)
     * @return Collection of built-in plot type definitions
     */
    Collection<PlotTypeDefinition> getBuiltInPlotTypes();

    /**
     * Check if a plot type is registered
     * @param typeName The type name to check
     * @return true if registered, false otherwise
     */
    boolean isPlotTypeRegistered(String typeName);

    /**
     * Check if a plot type is enabled
     * @param typeName The type name to check
     * @return true if the type exists and is enabled, false otherwise
     */
    boolean isPlotTypeEnabled(String typeName);

    /**
     * Enable or disable a plot type
     * @param typeName The type name to modify
     * @param enabled Whether to enable or disable the type
     * @return true if the type was found and modified, false otherwise
     */
    boolean setPlotTypeEnabled(String typeName, boolean enabled);

    /**
     * Disable a plot type
     * @param typeName The type name to disable
     * @return true if the type was found and disabled, false otherwise
     */
    default boolean disablePlotType(String typeName) {
        return setPlotTypeEnabled(typeName, false);
    }

    /**
     * Enable a plot type
     * @param typeName The type name to enable
     * @return true if the type was found and enabled, false otherwise
     */
    default boolean enablePlotType(String typeName) {
        return setPlotTypeEnabled(typeName, true);
    }

    /**
     * Get the number of registered plot types
     * @return Total count of registered plot types
     */
    int getPlotTypeCount();

    /**
     * Get the number of plot types registered by a specific plugin
     * @param pluginName The plugin name to count types for
     * @return Number of plot types from the specified plugin
     */
    int getPlotTypeCount(String pluginName);

    /**
     * Clear all plot types registered by a specific plugin
     * @param pluginName The plugin name to clear types for
     * @return Number of types that were removed
     */
    int clearPluginTypes(String pluginName);

    /**
     * Migrate built-in plot types from the legacy system
     * This should be called during server startup to ensure built-in types are available
     */
    void registerBuiltInTypes();
}