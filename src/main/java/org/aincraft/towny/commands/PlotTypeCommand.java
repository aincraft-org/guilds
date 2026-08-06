package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeHandler;
import org.aincraft.towny.plot.PlotTypeHandlerManager;
import org.aincraft.towny.plot.PlotTypeRegistry;
import org.aincraft.towny.services.PlotTypeService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.models.TownBlock;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Administrative commands for managing the extensible plot type system
 * Provides comprehensive management tools for server administrators
 */
public class PlotTypeCommand implements CommandExecutor, TabCompleter {

    private final PlotTypeService plotTypeService;
    private final PlotService plotService;
    private final PlotTypeRegistry plotTypeRegistry;

    @Inject
    public PlotTypeCommand(PlotTypeService plotTypeService,
                           PlotService plotService,
                           PlotTypeRegistry plotTypeRegistry) {
        this.plotTypeService = plotTypeService;
        this.plotService = plotService;
        this.plotTypeRegistry = plotTypeRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("towny.admin.plottype")) {
            sender.sendMessage("§cYou don't have permission to use plot type commands.");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "list":
                handleList(sender, subArgs);
                break;
            case "info":
                handleInfo(sender, subArgs);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "set":
                handleSet(sender, subArgs);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "cleanup":
                handleCleanup(sender, subArgs);
                break;
            case "migrate":
                handleMigrate(sender);
                break;
            case "test":
                handleTest(sender, subArgs);
                break;
            default:
                sender.sendMessage("§cUnknown subcommand: " + subCommand);
                showHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Handle the list subcommand
     */
    private void handleList(CommandSender sender, String[] args) {
        boolean showDetails = args.length > 0 && args[0].equalsIgnoreCase("detail");
        boolean showBuiltInOnly = args.length > 0 && args[0].equalsIgnoreCase("builtin");
        boolean showCustomOnly = args.length > 0 && args[0].equalsIgnoreCase("custom");

        Collection<PlotTypeDefinition> plotTypes = plotTypeService.getAllPlotTypes();

        if (showBuiltInOnly) {
            plotTypes = plotTypes.stream()
                    .filter(PlotTypeDefinition::isBuiltIn)
                    .collect(Collectors.toList());
        } else if (showCustomOnly) {
            plotTypes = plotTypes.stream()
                    .filter(def -> !def.isBuiltIn())
                    .collect(Collectors.toList());
        }

        sender.sendMessage("§6=== Plot Types ===");
        sender.sendMessage("§7Total registered: §e" + plotTypes.size());

        if (plotTypes.isEmpty()) {
            sender.sendMessage("§7No plot types found matching the criteria.");
            return;
        }

        for (PlotTypeDefinition definition : plotTypes) {
            String status = definition.isEnabled() ? "§aEnabled" : "§cDisabled";
            String type = definition.isBuiltIn() ? "§bBuilt-in" : "§eCustom";
            String plugin = definition.getPluginName() != null ? " (by " + definition.getPluginName() + ")" : "";

            sender.sendMessage("§8• " + definition.getDisplayName() + " §7- " + status + " §7- " + type + plugin);

            if (showDetails) {
                sender.sendMessage("  §7ID: §f" + definition.getTypeName());
                sender.sendMessage("  §7Description: §f" + definition.getDescription());

                if (!definition.getRequiredPermissions().isEmpty()) {
                    sender.sendMessage("  §7Permissions: §f" + String.join(", ", definition.getRequiredPermissions()));
                }

                if (!definition.getAllMetadata().isEmpty()) {
                    sender.sendMessage("  §7Metadata: §f" + definition.getAllMetadata().size() + " properties");
                }
            }
        }

        if (!showDetails && !showBuiltInOnly && !showCustomOnly) {
            sender.sendMessage("§7Use §e/plottype list detail §7for more information.");
            sender.sendMessage("§7Use §e/plottype list builtin §7or §e/plottype list custom §7to filter.");
        }
    }

    /**
     * Handle the info subcommand
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /plottype info <plot_type_name>");
            return;
        }

        String typeName = args[0];
        Optional<PlotTypeDefinition> definitionOpt = plotTypeService.getPlotType(typeName);

        if (!definitionOpt.isPresent()) {
            sender.sendMessage("§cPlot type '" + typeName + "' not found.");
            return;
        }

        PlotTypeDefinition definition = definitionOpt.get();

        sender.sendMessage("§6=== Plot Type Information ===");
        sender.sendMessage("§7Name: §f" + definition.getTypeName());
        sender.sendMessage("§7Display: §f" + definition.getDisplayName());
        sender.sendMessage("§7Description: §f" + definition.getDescription());
        sender.sendMessage("§7Status: " + (definition.isEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Type: " + (definition.isBuiltIn() ? "§bBuilt-in" : "§eCustom"));

        if (definition.getPluginName() != null) {
            sender.sendMessage("§7Plugin: §f" + definition.getPluginName());
        }

        if (!definition.getRequiredPermissions().isEmpty()) {
            sender.sendMessage("§7Required Permissions:");
            for (String permission : definition.getRequiredPermissions()) {
                sender.sendMessage("  §8• §f" + permission);
            }
        }

        if (!definition.getAllMetadata().isEmpty()) {
            sender.sendMessage("§7Metadata Properties:");
            definition.getAllMetadata().forEach((key, value) ->
                sender.sendMessage("  §8• §f" + key + ": §e" + value)
            );
        }

        // Show statistics for this plot type
        List<PlotTypeHandler> handlers = plotTypeService.getHandlersForPlotType(typeName);
        sender.sendMessage("§7Registered Handlers: §e" + handlers.size());
        for (PlotTypeHandler handler : handlers) {
            sender.sendMessage("  §8• §f" + handler.getPluginName());
        }
    }

    /**
     * Handle the stats subcommand
     */
    private void handleStats(CommandSender sender) {
        PlotTypeService.PlotTypeStats stats = plotTypeService.getStats();

        sender.sendMessage("§6=== Plot Type System Statistics ===");

        // Registry stats
        PlotTypeRegistry.RegistryStats registryStats = stats.getRegistryStats();
        sender.sendMessage("§7Total Plot Types: §e" + registryStats.getTotalTypes());
        sender.sendMessage("§7Enabled: §a" + registryStats.getEnabledTypes());
        sender.sendMessage("§7Disabled: §c" + registryStats.getDisabledTypes());
        sender.sendMessage("§7Built-in: §b" + registryStats.getBuiltInTypes());
        sender.sendMessage("§7Custom: §e" + registryStats.getPluginTypes());
        sender.sendMessage("§7Plugins with Types: §e" + registryStats.getPluginCount());

        // Handler stats
        PlotTypeHandlerManager.HandlerStats handlerStats = stats.getHandlerStats();
        sender.sendMessage("§7Total Handlers: §e" + handlerStats.getTotalHandlers());
        sender.sendMessage("§7Enabled Handlers: §a" + handlerStats.getEnabledHandlers());
        sender.sendMessage("§7Types with Handlers: §e" + handlerStats.getTypeCount());
        sender.sendMessage("§7Plugins with Handlers: §e" + handlerStats.getPluginCount());
    }

    /**
     * Handle the set subcommand
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /plottype set <plot_id> <plot_type>");
            return;
        }

        Player player = (Player) sender;
        UUID plotId;

        try {
            plotId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid plot ID: " + args[0]);
            return;
        }

        String plotType = args[1];

        if (!plotTypeService.isPlotTypeRegistered(plotType)) {
            sender.sendMessage("§cPlot type '" + plotType + "' is not registered.");
            return;
        }

        Optional<TownBlock> townBlockOpt = plotService.getTownBlock(plotId);
        if (!townBlockOpt.isPresent()) {
            sender.sendMessage("§cPlot with ID " + args[0] + " not found.");
            return;
        }

        boolean success = plotTypeService.changePlotType(plotId, plotType);

        if (success) {
            sender.sendMessage("§aSuccessfully changed plot type to " + plotType + "!");

            // Show new plot type info
            Optional<PlotTypeDefinition> definitionOpt = plotTypeService.getPlotType(plotType);
            definitionOpt.ifPresent(definition -> {
                sender.sendMessage("§7Plot Type: §f" + definition.getDisplayName());
                sender.sendMessage("§7Description: §f" + definition.getDescription());
            });
        } else {
            sender.sendMessage("§cFailed to change plot type. Check console for errors.");
        }
    }

    /**
     * Handle the reload subcommand
     */
    private void handleReload(CommandSender sender) {
        try {
            // Initialize built-in plot types
            plotTypeService.initializeBuiltInTypes();

            sender.sendMessage("§aBuilt-in plot types reloaded successfully!");
            sender.sendMessage("§7Total plot types now registered: §e" +
                             plotTypeService.getAllPlotTypes().size());
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload plot types: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle the cleanup subcommand
     */
    private void handleCleanup(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /plottype cleanup <plugin_name>");
            sender.sendMessage("§7Use §e/all §7to cleanup all unregistered plugin types.");
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            // Cleanup all unregistered plugin types
            List<String> registeredPlugins = plotTypeService.getRegisteredPlugins();
            int totalCleaned = 0;

            for (String pluginName : registeredPlugins) {
                int cleaned = plotTypeService.cleanupPlugin(pluginName);
                totalCleaned += cleaned;
                if (cleaned > 0) {
                    sender.sendMessage("§7Cleaned §e" + cleaned + " §7entries from plugin: §f" + pluginName);
                }
            }

            sender.sendMessage("§aTotal cleanup completed: §e" + totalCleaned + " §7entries removed.");
        } else {
            String pluginName = args[0];
            int cleaned = plotTypeService.cleanupPlugin(pluginName);

            if (cleaned > 0) {
                sender.sendMessage("§aCleaned §e" + cleaned + " §7entries from plugin: §f" + pluginName);
            } else {
                sender.sendMessage("§7No entries found for plugin: §f" + pluginName);
            }
        }
    }

    /**
     * Handle the migrate subcommand
     */
    private void handleMigrate(CommandSender sender) {
        sender.sendMessage("§6Plot Type Migration");
        sender.sendMessage("§7This command would trigger migration of existing plot types");
        sender.sendMessage("§7to the new registry system. This is handled automatically");
        sender.sendMessage("§7during database initialization.");

        // Show current migration status
        Collection<PlotTypeDefinition> plotTypes = plotTypeService.getAllPlotTypes();
        long builtInCount = plotTypes.stream().filter(PlotTypeDefinition::isBuiltIn).count();
        long customCount = plotTypes.stream().filter(def -> !def.isBuiltIn()).count();

        sender.sendMessage("§7Status:");
        sender.sendMessage("  §7Built-in types: §a" + builtInCount);
        sender.sendMessage("  §7Custom types: §e" + customCount);
        sender.sendMessage("  §7Total: §f" + plotTypes.size());
    }

    /**
     * Handle the test subcommand (for development/testing)
     */
    private void handleTest(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /plottype test <action>");
            sender.sendMessage("§7Available actions: registry, handlers, events");
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "registry":
                testRegistry(sender);
                break;
            case "handlers":
                testHandlers(sender);
                break;
            case "events":
                testEvents(sender);
                break;
            default:
                sender.sendMessage("§cUnknown test action: " + action);
                break;
        }
    }

    private void testRegistry(CommandSender sender) {
        try {
            boolean registryWorking = plotTypeRegistry.getPlotType("default").isPresent();
            sender.sendMessage("§7Registry Test: " + (registryWorking ? "§aPASSED" : "§cFAILED"));

            Collection<PlotTypeDefinition> allTypes = plotTypeRegistry.getAllPlotTypes();
            sender.sendMessage("§7Registry contains §e" + allTypes.size() + " §7plot types");
        } catch (Exception e) {
            sender.sendMessage("§cRegistry test failed: " + e.getMessage());
        }
    }

    private void testHandlers(CommandSender sender) {
        try {
            List<PlotTypeHandler> allHandlers = plotTypeService.getAllHandlers();
            sender.sendMessage("§7Handler Test: §a" + allHandlers.size() + " §7handlers registered");

            for (PlotTypeHandler handler : allHandlers) {
                sender.sendMessage("  §8• §f" + handler.getPluginName() +
                                 " §7- handles: " + Arrays.toString(handler.getHandledPlotTypes()));
            }
        } catch (Exception e) {
            sender.sendMessage("§cHandler test failed: " + e.getMessage());
        }
    }

    private void testEvents(CommandSender sender) {
        sender.sendMessage("§7Event Test: Event system is ready for testing");
        sender.sendMessage("§7Try entering different plot types to see events in action");
    }

    /**
     * Show help information
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Plot Type Admin Commands ===");
        sender.sendMessage("§e/plottype list [detail|builtin|custom] §7- List all plot types");
        sender.sendMessage("§e/plottype info <type> §7- Show detailed plot type information");
        sender.sendMessage("§e/plottype stats §7- Show system statistics");
        sender.sendMessage("§e/plottype set <plot_id> <type> §7- Change plot type");
        sender.sendMessage("§e/plottype reload §7- Reload built-in plot types");
        sender.sendMessage("§e/plottype cleanup <plugin|all> §7- Cleanup plugin data");
        sender.sendMessage("§e/plottype migrate §7- Show migration status");
        sender.sendMessage("§e/plottype test <action> §7- Run system tests");
        sender.sendMessage("§7Permission required: towny.admin.plottype");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("towny.admin.plottype")) {
            return completions;
        }

        if (args.length == 1) {
            // Subcommands
            String[] subcommands = {"list", "info", "stats", "set", "reload", "cleanup", "migrate", "test"};
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "list":
                    String[] options = {"detail", "builtin", "custom"};
                    for (String opt : options) {
                        if (opt.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(opt);
                        }
                    }
                    break;

                case "info":
                case "set":
                    // Plot type names
                    for (PlotTypeDefinition definition : plotTypeService.getAllPlotTypes()) {
                        if (definition.getTypeName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(definition.getTypeName());
                        }
                    }
                    break;

                case "cleanup":
                    String[] cleanupOptions = {"all"};
                    for (String opt : cleanupOptions) {
                        if (opt.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(opt);
                        }
                    }
                    // Add plugin names
                    for (String plugin : plotTypeService.getRegisteredPlugins()) {
                        if (plugin.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(plugin);
                        }
                    }
                    break;

                case "test":
                    String[] testOptions = {"registry", "handlers", "events"};
                    for (String opt : testOptions) {
                        if (opt.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(opt);
                        }
                    }
                    break;
            }
        }

        return completions;
    }
}