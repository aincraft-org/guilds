package org.aincraft.towny.services.impl;

import com.google.inject.Singleton;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeHandler;
import org.aincraft.towny.plot.PlotTypeHandlerManager;
import org.aincraft.towny.plot.PlotTypeRegistry;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PlotTypeService;

import com.google.inject.Inject;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of PlotTypeService
 * Provides comprehensive plot type management functionality
 */
@Singleton
public class PlotTypeServiceImpl implements PlotTypeService {

    private final PlotTypeRegistry plotTypeRegistry;
    private final PlotTypeHandlerManager handlerManager;
    private final PlotService plotService;
    private final Logger logger;

    @Inject
    public PlotTypeServiceImpl(PlotTypeRegistry plotTypeRegistry,
                              PlotTypeHandlerManager handlerManager,
                              PlotService plotService,
                              Logger logger) {
        this.plotTypeRegistry = plotTypeRegistry;
        this.handlerManager = handlerManager;
        this.plotService = plotService;
        this.logger = logger;
    }

    // Plot Type Registry Operations

    @Override
    public boolean registerPlotType(String pluginName, PlotTypeDefinition definition) {
        try {
            if (pluginName != null) {
                plotTypeRegistry.registerPlotType(pluginName, definition);
            } else {
                plotTypeRegistry.registerPlotType(definition);
            }
            return true;
        } catch (IllegalArgumentException e) {
            logger.warning("Failed to register plot type '" + definition.getTypeName() + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unregisterPlotType(String typeName) {
        return plotTypeRegistry.unregisterPlotType(typeName);
    }

    @Override
    public Optional<PlotTypeDefinition> getPlotType(String typeName) {
        return plotTypeRegistry.getPlotType(typeName);
    }

    @Override
    public Collection<PlotTypeDefinition> getAllPlotTypes() {
        return plotTypeRegistry.getAllPlotTypes();
    }

    @Override
    public Collection<PlotTypeDefinition> getPlotTypesByPlugin(String pluginName) {
        return plotTypeRegistry.getPlotTypesByPlugin(pluginName);
    }

    @Override
    public boolean isPlotTypeRegistered(String typeName) {
        return plotTypeRegistry.isPlotTypeRegistered(typeName);
    }

    @Override
    public boolean setPlotTypeEnabled(String typeName, boolean enabled) {
        return plotTypeRegistry.setPlotTypeEnabled(typeName, enabled);
    }

    // Handler Management Operations

    @Override
    public boolean registerHandler(String pluginName, String plotTypeName, PlotTypeHandler handler) {
        try {
            handlerManager.registerHandler(plotTypeName, handler);
            return true;
        } catch (IllegalArgumentException e) {
            logger.warning("Failed to register handler for plot type '" + plotTypeName + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unregisterHandler(String plotTypeName) {
        return handlerManager.unregisterHandler(plotTypeName);
    }

    @Override
    public int unregisterPluginHandlers(String pluginName) {
        List<PlotTypeHandler> handlers = new ArrayList<>(handlerManager.getAllHandlers());
        int count = 0;

        for (PlotTypeHandler handler : handlers) {
            if (handler.getPluginName().equals(pluginName)) {
                // Remove from all plot types this handler is registered for
                String[] handledTypes = handler.getHandledPlotTypes();
                for (String plotType : handledTypes) {
                    if (handlerManager.unregisterHandler(plotType)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public List<PlotTypeHandler> getHandlersForPlotType(String plotTypeName) {
        return new ArrayList<>(handlerManager.getHandlersForPlotType(plotTypeName));
    }

    @Override
    public List<PlotTypeHandler> getAllHandlers() {
        return new ArrayList<>(handlerManager.getAllHandlers());
    }

    // Town Block Integration

    @Override
    public boolean changePlotType(UUID townBlockId, String newPlotType) {
        if (!plotTypeRegistry.isPlotTypeRegistered(newPlotType)) {
            logger.warning("Cannot change plot type: '" + newPlotType + "' is not registered");
            return false;
        }

        Optional<TownBlock> townBlockOpt = plotService.getTownBlock(townBlockId);
        if (!townBlockOpt.isPresent()) {
            logger.warning("Cannot change plot type: TownBlock not found with ID " + townBlockId);
            return false;
        }

        TownBlock townBlock = townBlockOpt.get();
        String oldPlotType = townBlock.getPlotType();

        // Change the plot type
        townBlock.setPlotType(newPlotType);
        TownBlock updatedTownBlock = plotService.updateTownBlock(townBlock);

        if (updatedTownBlock != null) {
            logger.info("Changed plot type for TownBlock " + townBlockId + " from '" + oldPlotType + "' to '" + newPlotType + "'");

            // Dispatch plot type change event
            handlerManager.dispatchPlotTypeChangeEvent(null, townBlock, oldPlotType, newPlotType);
        }

        return updatedTownBlock != null;
    }

    @Override
    public Optional<String> getPlotType(UUID townBlockId) {
        Optional<TownBlock> townBlockOpt = plotService.getTownBlock(townBlockId);
        return townBlockOpt.map(TownBlock::getPlotType);
    }

    @Override
    public Optional<PlotTypeDefinition> getPlotTypeDefinition(UUID townBlockId) {
        Optional<String> plotTypeName = getPlotType(townBlockId);
        return plotTypeName.flatMap(plotTypeRegistry::getPlotType);
    }

    // Statistics and Information

    @Override
    public PlotTypeRegistry.RegistryStats getRegistryStats() {
        return plotTypeRegistry.getStats();
    }

    @Override
    public PlotTypeHandlerManager.HandlerStats getHandlerStats() {
        return handlerManager.getStats();
    }

    @Override
    public PlotTypeStats getStats() {
        return new PlotTypeStats(getRegistryStats(), getHandlerStats());
    }

    // Plugin Lifecycle

    @Override
    public void initializeBuiltInTypes() {
        plotTypeRegistry.registerBuiltInTypes();
    }

    @Override
    public int cleanupPlugin(String pluginName) {
        int count = 0;

        // Remove plot types
        Collection<PlotTypeDefinition> pluginTypes = plotTypeRegistry.getPlotTypesByPlugin(pluginName);
        for (PlotTypeDefinition definition : pluginTypes) {
            if (plotTypeRegistry.unregisterPlotType(definition.getTypeName())) {
                count++;
            }
        }

        // Remove handlers
        count += unregisterPluginHandlers(pluginName);

        if (count > 0) {
            logger.info("Cleaned up " + count + " plot type entries for plugin: " + pluginName);
        }

        return count;
    }

    @Override
    public List<String> getRegisteredPlugins() {
        Collection<PlotTypeDefinition> allTypes = plotTypeRegistry.getAllPlotTypes();
        return allTypes.stream()
                .map(PlotTypeDefinition::getPluginName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

}