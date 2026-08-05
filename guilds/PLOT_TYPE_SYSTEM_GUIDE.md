# Extensible Plot Type System - Usage Guide

This guide demonstrates how to use the new extensible plot type system in Towny. The system allows plugins to register custom plot types with rich metadata and respond to player actions within specific plot types.

## Quick Start

### 1. Basic Plugin Setup

```java
public class MyPlotPlugin extends JavaPlugin implements PlotTypeHandler {

    @Override
    public void onEnable() {
        // Register your plot type
        registerMyPlotType();

        // Register the handler
        registerMyHandler();
    }

    private void registerMyPlotType() {
        PlotTypeDefinition myPlotType = PlotTypeDefinition.builder()
            .typeName("custom_plot")
            .displayName("Custom Plot")
            .description("My awesome custom plot type")
            .pluginName(getName())
            .metadata("feature_1", true)
            .metadata("feature_2", 1.5)
            .build();

        // Get the Towny PlotTypeService and register
        PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();
        plotTypeService.registerPlotType(getName(), myPlotType);
    }

    private void registerMyHandler() {
        PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();
        plotTypeService.registerHandler(getName(), "custom_plot", this);
    }
}
```

### 2. Handling Plot Type Events

```java
@Override
public void onPlayerEnter(PlotTypeEvent.PlayerEnterPlotEvent event) {
    Player player = event.getPlayer();
    player.sendMessage("§aWelcome to my custom plot!");
}

@Override
public void onPlayerLeave(PlotTypeEvent.PlayerLeavePlotEvent event) {
    Player player = event.getPlayer();
    player.sendMessage("§7Leaving custom plot area");
}

@Override
public void onPlotAction(PlotTypeEvent.PlotActionEvent event) {
    Player player = event.getPlayer();
    String action = event.getAction();

    switch (action) {
        case "build":
            handleCustomBuilding(event);
            break;
        case "interact":
            handleCustomInteraction(event);
            break;
    }
}
```

## Advanced Features

### 1. Rich Metadata System

Plot types can store arbitrary metadata for custom functionality:

```java
PlotTypeDefinition advancedPlot = PlotTypeDefinition.builder()
    .typeName("advanced_plot")
    .displayName("Advanced Plot")
    .description("Plot with advanced features")
    .pluginName(getName())
    // Simple values
    .metadata("level_requirement", 5)
    .metadata("cost_per_hour", 10.0)
    // Complex objects
    .metadata("allowed_items", Arrays.asList("DIAMOND_PICKAXE", "IRON_SWORD"))
    .metadata("effects", Map.of("speed", 1.2, "jump", 1.5))
    // Nested structures
    .metadata("permissions", Map.of(
        "build", "advanced.build",
        "interact", "advanced.interact"
    ))
    .build();
```

### 2. Permission Requirements

Require specific permissions for plot types:

```java
PlotTypeDefinition vipPlot = PlotTypeDefinition.builder()
    .typeName("vip_lounge")
    .displayName("VIP Lounge")
    .description("Exclusive area for VIP players")
    .pluginName(getName())
    .requirePermission("towny.vip")
    .requirePermission("premium lounge.access")
    .build();
```

### 3. Plugin Lifecycle Management

```java
public class MyPlugin extends JavaPlugin {
    private PlotTypeService plotTypeService;
    private String plotTypeName = "my_custom_plot";

    @Override
    public void onEnable() {
        plotTypeService = TownyAPI.getPlotTypeService();

        // Register plot type and handlers
        registerPlotType();
        registerHandlers();
    }

    @Override
    public void onDisable() {
        // Clean up - Towny will automatically clean up
        // when the plugin disables, but you can explicitly
        // call cleanup if needed
        if (plotTypeService != null) {
            plotTypeService.cleanupPlugin(getName());
        }
    }

    private void registerPlotType() {
        // ... plot type registration
    }

    private void registerHandlers() {
        // ... handler registration
    }
}
```

## Event System

### Available Events

1. **PlayerEnterPlotEvent** - Fired when player enters a plot of your type
2. **PlayerLeavePlotEvent** - Fired when player leaves your plot type
3. **PlotActionEvent** - Fired for custom actions within your plot
4. **PlotTypeChangeEvent** - Fired when plot type changes to/from your type

### Custom Action Events

You can dispatch custom action events from anywhere:

```java
// In your code when a player performs a specific action
PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();
PlotTypeHandlerManager handlerManager = // get from injection or service

// Dispatch a custom action
handlerManager.dispatchPlotActionEvent(
    player,
    townBlock,
    "custom_action",
    Map.of("data", "value", "amount", 42)
);
```

## Integration with Town Services

### Changing Plot Types

```java
PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();

// Change a plot's type
boolean success = plotTypeService.changePlotType(townBlockId, "my_custom_plot");

if (success) {
    player.sendMessage("§aPlot type changed successfully!");
}
```

### Querying Plot Information

```java
PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();

// Get plot type of a specific plot
Optional<String> plotType = plotTypeService.getPlotType(townBlockId);

// Get full plot type definition
Optional<PlotTypeDefinition> definition = plotTypeService.getPlotTypeDefinition(townBlockId);

if (definition.isPresent()) {
    PlotTypeDefinition def = definition.get();
    player.sendMessage("§ePlot Type: " + def.getDisplayName());

    // Check metadata
    double cost = def.getMetadata("cost_per_hour", Double.class, 0.0);
    if (cost > 0) {
        player.sendMessage("§7Cost per hour: §e$" + cost);
    }
}
```

## Examples Included

### 1. Farm Plot Example (`FarmPlotPluginExample`)

Demonstrates:
- Growth multipliers and seasonal effects
- Auto-harvest functionality
- Enhanced fertilizing mechanics
- Immersive seasonal notifications

### 2. Shop Plot Example (`ShopPlotPluginExample`)

Demonstrates:
- Shop configuration and management
- Customer attraction features
- Transaction handling with taxes
- Advertisement broadcasting
- Custom inventory interactions

## Built-in Plot Types

The system includes these built-in plot types:
- **Default** - Standard residential plot
- **Shop** - Commercial area for markets
- **Farm** - Agricultural plot with growth bonuses
- **Bank** - Financial services area
- **Inn** - Hospitality and accommodation
- **Embassy** - Diplomatic representation
- **Jail** - Law enforcement area
- **Arena** - Combat and entertainment
- **Wilderness** - Unclaimed territory

## Best Practices

### 1. Plugin Naming

Always use your plugin name when registering plot types:
```java
plotTypeService.registerPlotType(getName(), plotTypeDefinition);
plotTypeService.registerHandler(getName(), "plot_type", handler);
```

### 2. Error Handling

Always handle exceptions gracefully:
```java
try {
    boolean success = plotTypeService.registerPlotType(getName(), definition);
    if (success) {
        getLogger().info("Plot type registered successfully");
    } else {
        getLogger().warning("Failed to register plot type");
    }
} catch (Exception e) {
    getLogger().severe("Error registering plot type: " + e.getMessage());
}
```

### 3. Resource Cleanup

Implement proper cleanup in onDisable():
```java
@Override
public void onDisable() {
    PlotTypeService plotTypeService = TownyAPI.getPlotTypeService();
    if (plotTypeService != null) {
        int cleaned = plotTypeService.cleanupPlugin(getName());
        getLogger().info("Cleaned up " + cleaned + " plot type entries");
    }
}
```

### 4. Performance Considerations

- Use metadata sparingly to avoid excessive memory usage
- Implement efficient event handlers
- Cache frequently accessed plot type definitions
- Use async operations for heavy computations

## Troubleshooting

### Common Issues

1. **Plot type not found**: Ensure the plot type is registered before use
2. **Events not firing**: Check that handlers are properly registered
3. **Permission denied**: Verify required permissions are set correctly
4. **Memory leaks**: Ensure proper cleanup in plugin disable

### Debug Commands

Use these commands for debugging (when implemented):
- `/admintype stats` - Show registry statistics
- `/admintype list` - List all registered plot types
- `/admintype plugin list` - List plugins with registered types

## API Reference

### PlotTypeDefinition.Builder Methods

- `typeName(String)` - Set unique type identifier
- `displayName(String)` - Set human-readable name
- `description(String)` - Set description
- `pluginName(String)` - Set plugin name (null for built-in)
- `metadata(String, Object)` - Add metadata property
- `requirePermission(String)` - Add required permission
- `enabled(boolean)` - Set enabled state
- `build()` - Create the definition

### PlotTypeService Methods

- `registerPlotType(String, PlotTypeDefinition)` - Register plot type
- `unregisterPlotType(String)` - Unregister plot type
- `getPlotType(String)` - Get plot type by name
- `changePlotType(UUID, String)` - Change plot type
- `getPlotTypeDefinition(UUID)` - Get definition for plot
- `getStats()` - Get system statistics

This extensible plot type system provides powerful customization capabilities while maintaining compatibility with existing Towny functionality. Happy coding!