package org.aincraft.towny.plot;

import com.google.inject.Singleton;
import org.aincraft.towny.models.TownBlock;

import com.google.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe implementation of PlotTypeRegistry
 * Uses ConcurrentHashMap for thread-safe operations and provides comprehensive type management
 */
@Singleton
public class PlotTypeRegistryImpl implements PlotTypeRegistry {

    private final Map<String, PlotTypeDefinition> plotTypes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> pluginTypes = new ConcurrentHashMap<>();
    private final Logger logger;

    @Inject
    public PlotTypeRegistryImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void registerPlotType(PlotTypeDefinition definition) {
        Objects.requireNonNull(definition, "Plot type definition cannot be null");

        String typeName = definition.getTypeName();

        if (plotTypes.containsKey(typeName)) {
            throw new IllegalArgumentException("Plot type '" + typeName + "' is already registered");
        }

        plotTypes.put(typeName, definition);

        String pluginName = definition.getPluginName();
        if (pluginName != null) {
            pluginTypes.computeIfAbsent(pluginName, k -> ConcurrentHashMap.newKeySet())
                      .add(typeName);
        }

        logger.info("Registered plot type: " + definition);
    }

    @Override
    public void registerPlotType(String pluginName, PlotTypeDefinition definition) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");

        // Create a copy with the plugin name set
        PlotTypeDefinition.Builder builder = definition.toBuilder()
                .pluginName(pluginName);

        PlotTypeDefinition pluginDefinition = builder.build();
        registerPlotType(pluginDefinition);
    }

    @Override
    public boolean unregisterPlotType(String typeName) {
        Objects.requireNonNull(typeName, "Type name cannot be null");

        PlotTypeDefinition definition = plotTypes.remove(typeName);
        if (definition == null) {
            return false;
        }

        String pluginName = definition.getPluginName();
        if (pluginName != null) {
            Set<String> types = pluginTypes.get(pluginName);
            if (types != null) {
                types.remove(typeName);
                if (types.isEmpty()) {
                    pluginTypes.remove(pluginName);
                }
            }
        }

        logger.info("Unregistered plot type: " + typeName);
        return true;
    }

    @Override
    public Optional<PlotTypeDefinition> getPlotType(String typeName) {
        Objects.requireNonNull(typeName, "Type name cannot be null");
        return Optional.ofNullable(plotTypes.get(typeName.toLowerCase()));
    }

    @Override
    public Collection<PlotTypeDefinition> getAllPlotTypes() {
        return new ArrayList<>(plotTypes.values());
    }

    @Override
    public Collection<PlotTypeDefinition> getPlotTypesByPlugin(String pluginName) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");

        Set<String> typeNames = pluginTypes.get(pluginName);
        if (typeNames == null) {
            return Collections.emptyList();
        }

        List<PlotTypeDefinition> result = new ArrayList<>();
        for (String typeName : typeNames) {
            PlotTypeDefinition definition = plotTypes.get(typeName);
            if (definition != null) {
                result.add(definition);
            }
        }
        return result;
    }

    @Override
    public Collection<PlotTypeDefinition> getBuiltInPlotTypes() {
        List<PlotTypeDefinition> result = new ArrayList<>();
        for (PlotTypeDefinition definition : plotTypes.values()) {
            if (definition.isBuiltIn()) {
                result.add(definition);
            }
        }
        return result;
    }

    @Override
    public boolean isPlotTypeRegistered(String typeName) {
        Objects.requireNonNull(typeName, "Type name cannot be null");
        return plotTypes.containsKey(typeName.toLowerCase());
    }

    @Override
    public boolean isPlotTypeEnabled(String typeName) {
        Optional<PlotTypeDefinition> definition = getPlotType(typeName);
        return definition.isPresent() && definition.get().isEnabled();
    }

    @Override
    public boolean setPlotTypeEnabled(String typeName, boolean enabled) {
        Optional<PlotTypeDefinition> definitionOpt = getPlotType(typeName);
        if (!definitionOpt.isPresent()) {
            return false;
        }

        PlotTypeDefinition definition = definitionOpt.get();
        PlotTypeDefinition updatedDefinition = definition.toBuilder()
                .enabled(enabled)
                .build();

        plotTypes.put(typeName, updatedDefinition);
        logger.info("Plot type '" + typeName + "' " + (enabled ? "enabled" : "disabled"));
        return true;
    }

    @Override
    public int getPlotTypeCount() {
        return plotTypes.size();
    }

    @Override
    public int getPlotTypeCount(String pluginName) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");

        Set<String> typeNames = pluginTypes.get(pluginName);
        return typeNames != null ? typeNames.size() : 0;
    }

    @Override
    public int clearPluginTypes(String pluginName) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");

        Set<String> typeNames = pluginTypes.remove(pluginName);
        if (typeNames == null) {
            return 0;
        }

        int count = 0;
        for (String typeName : typeNames) {
            if (plotTypes.remove(typeName) != null) {
                count++;
            }
        }

        logger.info("Cleared " + count + " plot types from plugin: " + pluginName);
        return count;
    }

    @Override
    public void registerBuiltInTypes() {
        if (plotTypes.isEmpty()) {
            registerBuiltInTypesNow();
        }
    }

    private void registerBuiltInTypesNow() {
        registerPlotType(PlotTypeDefinition.builder()
                .typeName("resident")
                .displayName("Resident")
                .description("Standard residential plot for town members")
                .pluginName(null)
                .build());

        registerPlotType(PlotTypeDefinition.builder()
                .typeName("farm")
                .displayName("Farm")
                .description("Agricultural plot for farming")
                .pluginName(null)
                .metadata("crop_growth_bonus", 1.2)
                .build());

        registerPlotType(PlotTypeDefinition.builder()
                .typeName("blacksmith")
                .displayName("Blacksmith")
                .description("Crafting and smithing plot")
                .pluginName(null)
                .metadata("smithing_bonus", true)
                .build());

        registerPlotType(PlotTypeDefinition.builder()
                .typeName("bank")
                .displayName("Bank")
                .description("Financial services plot")
                .pluginName(null)
                .metadata("bank_services", Arrays.asList("deposit", "withdraw", "exchange"))
                .build());

        registerPlotType(PlotTypeDefinition.builder()
                .typeName("storage")
                .displayName("Storage")
                .description("Storage and warehouse plot")
                .pluginName(null)
                .metadata("storage_capacity_bonus", 1.5)
                .build());

        logger.info("Registered " + getBuiltInPlotTypes().size() + " built-in plot types");
    }

}