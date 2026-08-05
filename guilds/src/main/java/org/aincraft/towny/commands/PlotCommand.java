package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.models.Permission;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PlotTypeService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Command handler for plot-related operations
 */
public class PlotCommand implements CommandExecutor, TabCompleter {

    private final PlotService plotService;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotTypeService plotTypeService;
    private final Logger logger;

    @Inject
    public PlotCommand(PlotService plotService, ResidentService residentService,
                       TownService townService, PlotTypeService plotTypeService, Logger logger) {
        this.plotService = plotService;
        this.residentService = residentService;
        this.townService = townService;
        this.plotTypeService = plotTypeService;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "claim":
                handleClaim(player, args);
                break;

            case "buy":
                handleBuy(player, args);
                break;

            case "info":
                handleInfo(player, args);
                break;

            case "forsale":
                handleForSale(player, args);
                break;

            case "perm":
            case "permission":
                handlePermission(player, args);
                break;

            case "list":
                handleList(player, args);
                break;

            case "type":
                handleType(player, args);
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void handleClaim(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        // Check if plot exists and is owned by town (not a resident)
        if (!plotService.canResidentClaimPlot(playerUuid, chunkX, chunkZ, world)) {
            player.sendMessage(ChatColor.RED + "You cannot claim this plot. The town must claim the territory first.");
            return;
        }

        if (plotService.claimPlotForResident(playerUuid, chunkX, chunkZ, world)) {
            player.sendMessage(ChatColor.GREEN + "Plot claimed successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to claim plot.");
        }
    }

    private void handleBuy(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<TownBlock> plotOpt = plotService.getTownBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No plot found at this location.");
            return;
        }

        TownBlock plot = plotOpt.get();
        if (!plot.isForSale()) {
            player.sendMessage(ChatColor.RED + "This plot is not for sale.");
            return;
        }

        if (!plotService.canResidentAffordPlot(playerUuid, plot.getId())) {
            player.sendMessage(ChatColor.RED + "You cannot afford this plot.");
            return;
        }

        double price = plot.getPrice();
        if (plotService.buyPlot(playerUuid, plot.getId(), price)) {
            player.sendMessage(ChatColor.GREEN + String.format("Plot purchased for %.2f!", price));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to purchase plot.");
        }
    }

    private void handleInfo(Player player, String[] args) {
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<TownBlock> plotOpt = plotService.getTownBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No plot found at this location.");
            return;
        }

        TownBlock plot = plotOpt.get();
        displayPlotInfo(player, plot);
    }

    private void handleForSale(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /plot forsale <price>");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
            if (price < 0) {
                player.sendMessage(ChatColor.RED + "Price cannot be negative.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid price amount.");
            return;
        }

        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<TownBlock> plotOpt = plotService.getTownBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No plot found at this location.");
            return;
        }

        TownBlock plot = plotOpt.get();
        if (!plot.isOwner(playerUuid)) {
            player.sendMessage(ChatColor.RED + "You don't own this plot.");
            return;
        }

        if (plotService.setPlotForSale(plot.getId(), price, playerUuid)) {
            if (price > 0) {
                player.sendMessage(ChatColor.GREEN + String.format("Plot put up for sale for %.2f!", price));
            } else {
                player.sendMessage(ChatColor.GREEN + "Plot removed from sale.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to set plot for sale.");
        }
    }

    private void handlePermission(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /plot perm <set|add|remove|list|reset> [target] [permission] [value]");
            return;
        }

        String permAction = args[1].toLowerCase();
        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<TownBlock> plotOpt = plotService.getTownBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No plot found at this location.");
            return;
        }

        TownBlock plot = plotOpt.get();

        switch (permAction) {
            case "list":
                displayPlotPermissions(player, plot);
                break;

            case "reset":
                if (!plot.isOwner(playerUuid)) {
                    player.sendMessage(ChatColor.RED + "Only plot owners can reset permissions.");
                    return;
                }
                plot.resetToDefaultPermissions();
                plotService.updateTownBlock(plot);
                player.sendMessage(ChatColor.GREEN + "Plot permissions reset to default.");
                break;

            case "set":
            case "add":
            case "remove":
                handlePermissionModify(player, plot, args, permAction, playerUuid);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown permission action. Use: set, add, remove, list, reset");
                break;
        }
    }

    private void handlePermissionModify(Player player, TownBlock plot, String[] args, String action, UUID playerUuid) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /plot perm " + action + " <target> <permission> [true/false]");
            return;
        }

        String target = args[2].toLowerCase();
        String permissionName = args[3].toLowerCase();

        int permissionFlag = getPermissionFlagFromName(permissionName);
        if (permissionFlag == -1) {
            player.sendMessage(ChatColor.RED + "Unknown permission. Available: build, destroy, switch, item_use, all");
            return;
        }

        boolean value = true;
        if (action.equals("set") && args.length >= 5) {
            value = Boolean.parseBoolean(args[4]);
        } else if (action.equals("remove")) {
            value = false;
        }

        // For simplicity, directly modify plot permissions
        // In a full implementation, this would use the PermissionService for targeted permissions
        switch (action) {
            case "add":
                plot.addPermissionFlag(permissionFlag);
                break;
            case "remove":
                plot.removePermissionFlag(permissionFlag);
                break;
            case "set":
                plot.setPermissionFlag(permissionFlag, value);
                break;
        }

        if (plotService.updateTownBlock(plot) != null) {
            player.sendMessage(ChatColor.GREEN + String.format("Permission '%s' %s for target '%s'",
                                permissionName, action.equals("set") ? (value ? "granted" : "denied") : action, target));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to modify plot permission.");
        }
    }

    private void handleList(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();
        List<TownBlock> ownedPlots = plotService.getPlotsOwnedByResident(playerUuid);

        if (ownedPlots.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't own any plots.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Your plots (" + ownedPlots.size() + "):");
        for (TownBlock plot : ownedPlots) {
            String location = String.format("%s [%d,%d]", plot.getWorld(), plot.getX(), plot.getZ());
            String status = plot.isForSale() ? String.format(" (For sale: %.2f)", plot.getPrice()) : "";
            player.sendMessage(ChatColor.AQUA + "  - " + plot.getPlotTypeDisplayName() + " at " + location + status);
        }
    }

    private void displayPlotInfo(Player player, TownBlock plot) {
        player.sendMessage(ChatColor.GOLD + "=== Plot Information ===");
        player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + plot.getWorld() + " [" + plot.getX() + "," + plot.getZ() + "]");
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + plot.getPlotTypeDisplayName());

        if (plot.hasOwner()) {
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + "Player owned");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + "Town owned");
        }

        if (plot.isForSale()) {
            player.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.GREEN + String.format("%.2f", plot.getPrice()));
        }

        player.sendMessage(ChatColor.YELLOW + "Permissions:");
        for (String perm : plot.getActivePermissionNames()) {
            player.sendMessage(ChatColor.GRAY + "  - " + perm);
        }
    }

    private void displayPlotPermissions(Player player, TownBlock plot) {
        player.sendMessage(ChatColor.GOLD + "=== Plot Permissions ===");
        player.sendMessage(ChatColor.YELLOW + "Build: " + formatPermission(plot.hasPermission("build")));
        player.sendMessage(ChatColor.YELLOW + "Destroy: " + formatPermission(plot.hasPermission("destroy")));
        player.sendMessage(ChatColor.YELLOW + "Switch: " + formatPermission(plot.hasPermission("switch")));
        player.sendMessage(ChatColor.YELLOW + "Item Use: " + formatPermission(plot.hasPermission("item_use")));
    }

    private String formatPermission(boolean hasPermission) {
        return hasPermission ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Denied";
    }

    private int getPermissionFlagFromName(String permissionName) {
        switch (permissionName.toLowerCase()) {
            case "build":
                return Permission.Flag.BUILD;
            case "destroy":
                return Permission.Flag.DESTROY;
            case "switch":
                return Permission.Flag.SWITCH;
            case "item_use":
                return Permission.Flag.ITEM_USE;
            case "all":
                return Permission.Flag.BUILD_ALL;
            default:
                return -1;
        }
    }

    private void handleType(Player player, String[] args) {
        if (args.length == 0) {
            showTypeHelp(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleTypeList(player, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "info":
                handleTypeInfo(player, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "set":
                handleTypeSet(player, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "available":
                handleTypeAvailable(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown type subcommand: " + subCommand);
                showTypeHelp(player);
                break;
        }
    }

    private void showTypeHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Plot Type Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/plot type list [type]" + ChatColor.WHITE + " - List plot types");
        player.sendMessage(ChatColor.YELLOW + "/plot type info <type>" + ChatColor.WHITE + " - Show plot type details");
        player.sendMessage(ChatColor.YELLOW + "/plot type set <type>" + ChatColor.WHITE + " - Change current plot type");
        player.sendMessage(ChatColor.YELLOW + "/plot type available" + ChatColor.WHITE + " - Show available plot types for this plot");
    }

    private void handleTypeList(Player player, String[] args) {
        Collection<PlotTypeDefinition> plotTypes;

        if (args.length > 0) {
            // Show specific plot type
            String typeName = args[0];
            Optional<PlotTypeDefinition> definitionOpt = plotTypeService.getPlotType(typeName);

            if (!definitionOpt.isPresent()) {
                player.sendMessage(ChatColor.RED + "Plot type '" + typeName + "' not found.");
                return;
            }

            PlotTypeDefinition definition = definitionOpt.get();
            player.sendMessage(ChatColor.GOLD + "=== Plot Type: " + definition.getDisplayName() + " ===");
            player.sendMessage(ChatColor.WHITE + "ID: " + ChatColor.GRAY + definition.getTypeName());
            player.sendMessage(ChatColor.WHITE + "Description: " + ChatColor.GRAY + definition.getDescription());
            player.sendMessage(ChatColor.WHITE + "Status: " + (definition.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));

            if (definition.getPluginName() != null) {
                player.sendMessage(ChatColor.WHITE + "Plugin: " + ChatColor.GRAY + definition.getPluginName());
            }

            if (!definition.getRequiredPermissions().isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Required Permissions:");
                for (String perm : definition.getRequiredPermissions()) {
                    player.sendMessage(ChatColor.GRAY + "  • " + perm);
                }
            }

            if (!definition.getAllMetadata().isEmpty()) {
                player.sendMessage(ChatColor.WHITE + "Properties:");
                definition.getAllMetadata().forEach((key, value) ->
                    player.sendMessage(ChatColor.GRAY + "  • " + key + ": " + ChatColor.YELLOW + value));
            }
        } else {
            // List all plot types
            plotTypes = plotTypeService.getAllPlotTypes();
            player.sendMessage(ChatColor.GOLD + "=== Available Plot Types ===");
            player.sendMessage(ChatColor.GRAY + "Total: " + ChatColor.YELLOW + plotTypes.size() + " plot types");

            for (PlotTypeDefinition definition : plotTypes) {
                String status = definition.isEnabled() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                String type = definition.isBuiltIn() ? ChatColor.BLUE + "[B]" : ChatColor.YELLOW + "[C]";
                String plugin = definition.getPluginName() != null ? " (" + definition.getPluginName() + ")" : "";

                player.sendMessage(status + " " + type + " " + ChatColor.WHITE + definition.getDisplayName() + plugin);
            }
        }
    }

    private void handleTypeInfo(Player player, String[] args) {
        if (args.length == 0) {
            // Show info about current plot type
            int chunkX = player.getLocation().getChunk().getX();
            int chunkZ = player.getLocation().getChunk().getZ();
            String world = player.getWorld().getName();

            Optional<TownBlock> townBlockOpt = plotService.getTownBlock(chunkX, chunkZ, world);
            if (!townBlockOpt.isPresent()) {
                player.sendMessage(ChatColor.RED + "No plot found at your location.");
                return;
            }

            TownBlock townBlock = townBlockOpt.get();
            String currentType = townBlock.getEffectivePlotType();
            Optional<PlotTypeDefinition> definitionOpt = plotTypeService.getPlotType(currentType);

            player.sendMessage(ChatColor.GOLD + "=== Current Plot Type Information ===");
            player.sendMessage(ChatColor.WHITE + "Plot ID: " + ChatColor.GRAY + townBlock.getId().toString().substring(0, 8) + "...");
            player.sendMessage(ChatColor.WHITE + "Type: " + ChatColor.YELLOW + currentType);
            player.sendMessage(ChatColor.WHITE + "Is Built-in: " + ChatColor.GRAY + townBlock.isBuiltInPlotType());
            player.sendMessage(ChatColor.WHITE + "Category: " + ChatColor.YELLOW + townBlock.getPlotTypeCategory());
            player.sendMessage(ChatColor.WHITE + "Priority: " + ChatColor.YELLOW + townBlock.getPlotTypePriority());

            if (definitionOpt.isPresent()) {
                PlotTypeDefinition definition = definitionOpt.get();
                player.sendMessage(ChatColor.WHITE + "Display Name: " + ChatColor.YELLOW + definition.getDisplayName());
                player.sendMessage(ChatColor.WHITE + "Description: " + ChatColor.GRAY + definition.getDescription());
                player.sendMessage(ChatColor.WHITE + "Status: " + (definition.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
            }

            if (townBlock.hasPlotTypeDefinition()) {
                player.sendMessage(ChatColor.WHITE + "Has Extensible Definition: " + ChatColor.GREEN + "Yes");
            }
        } else {
            // Show info about specific plot type
            handleTypeInfo(player, new String[0]); // Reuse the method above
        }
    }

    private void handleTypeSet(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /plot type set <plot_type>");
            return;
        }

        String plotType = args[0];

        if (!plotTypeService.isPlotTypeRegistered(plotType)) {
            player.sendMessage(ChatColor.RED + "Plot type '" + plotType + "' is not registered.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/plot type available" + ChatColor.GRAY + " to see available types.");
            return;
        }

        // Check if player has permission for this plot type
        Optional<PlotTypeDefinition> definitionOpt = plotTypeService.getPlotType(plotType);
        if (definitionOpt.isPresent()) {
            PlotTypeDefinition definition = definitionOpt.get();
            for (String permission : definition.getRequiredPermissions()) {
                if (!player.hasPermission(permission)) {
                    player.sendMessage(ChatColor.RED + "You need permission '" + permission + "' to use this plot type.");
                    return;
                }
            }
        }

        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<TownBlock> townBlockOpt = plotService.getTownBlock(chunkX, chunkZ, world);
        if (!townBlockOpt.isPresent()) {
            player.sendMessage(ChatColor.RED + "No plot found at your location.");
            return;
        }

        TownBlock townBlock = townBlockOpt.get();

        // Check if player owns this plot or has town permissions
        if (!playerHasPlotPermission(player, townBlock)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to change this plot's type.");
            return;
        }

        boolean success = plotTypeService.changePlotType(townBlock.getId(), plotType);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Plot type changed to " + ChatColor.YELLOW + plotType + ChatColor.GREEN + "!");

            if (definitionOpt.isPresent()) {
                PlotTypeDefinition definition = definitionOpt.get();
                player.sendMessage(ChatColor.GRAY + "Description: " + definition.getDescription());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to change plot type. Please check the console.");
        }
    }

    private void handleTypeAvailable(Player player) {
        Collection<PlotTypeDefinition> allTypes = plotTypeService.getAllPlotTypes();

        player.sendMessage(ChatColor.GOLD + "=== Available Plot Types ===");

        // Get player's permissions
        Set<String> playerPermissions = new HashSet<>();
        for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            playerPermissions.add(attachment.getPermission());
        }

        int count = 0;
        for (PlotTypeDefinition definition : allTypes) {
            if (!definition.isEnabled()) {
                continue;
            }

            boolean hasAllPermissions = true;
            for (String requiredPerm : definition.getRequiredPermissions()) {
                if (!playerPermissions.contains(requiredPerm)) {
                    hasAllPermissions = false;
                    break;
                }
            }

            if (hasAllPermissions) {
                count++;
                String type = definition.isBuiltIn() ? ChatColor.BLUE + "[B]" : ChatColor.YELLOW + "[C]";
                String plugin = definition.getPluginName() != null ? " (" + definition.getPluginName() + ")" : "";

                player.sendMessage(ChatColor.GREEN + "✓ " + type + " " + ChatColor.WHITE + definition.getDisplayName() + plugin);
                player.sendMessage(ChatColor.GRAY + "  " + definition.getDescription());
            }
        }

        if (count == 0) {
            player.sendMessage(ChatColor.GRAY + "No plot types available with your current permissions.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Total available: " + ChatColor.YELLOW + count + " plot types");
        }
    }

    private boolean playerHasPlotPermission(Player player, TownBlock townBlock) {
        UUID playerUuid = player.getUniqueId();

        // Player owns the plot
        if (townBlock.isOwner(playerUuid)) {
            return true;
        }

        // Player is town admin (would need to check town membership and roles)
        // This is simplified - in a real implementation you'd check town permissions
        if (townBlock.getTownId() != null) {
            return player.hasPermission("towny.admin.plot") ||
                   player.hasPermission("towny.town.plot.type");
        }

        return false;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Plot Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/plot claim" + ChatColor.WHITE + " - Claim current chunk");
        player.sendMessage(ChatColor.YELLOW + "/plot buy" + ChatColor.WHITE + " - Buy plot that's for sale");
        player.sendMessage(ChatColor.YELLOW + "/plot info" + ChatColor.WHITE + " - Show plot information");
        player.sendMessage(ChatColor.YELLOW + "/plot forsale <price>" + ChatColor.WHITE + " - Put plot up for sale");
        player.sendMessage(ChatColor.YELLOW + "/plot perm <set|add|remove|list|reset>" + ChatColor.WHITE + " - Manage permissions");
        player.sendMessage(ChatColor.YELLOW + "/plot list" + ChatColor.WHITE + " - List your plots");
        player.sendMessage(ChatColor.YELLOW + "/plot type" + ChatColor.WHITE + " - Manage plot types");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("claim", "buy", "info", "forsale", "perm", "list", "type"));
        } else if (args[0].equalsIgnoreCase("type") && args.length == 2) {
            completions.addAll(Arrays.asList("list", "info", "set", "available"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("perm")) {
            completions.addAll(Arrays.asList("set", "add", "remove", "list", "reset"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("perm") &&
                   (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")))) {
            completions.addAll(Arrays.asList("all", "resident", "town", "assistant", "mayor"));
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("perm") &&
                   (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")))) {
            completions.addAll(Arrays.asList("build", "destroy", "switch", "item_use", "all"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("perm") && args[1].equalsIgnoreCase("set")) {
            completions.addAll(Arrays.asList("true", "false"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("type") &&
                  (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("set"))) {
            // Add plot type name completions
            for (PlotTypeDefinition definition : plotTypeService.getAllPlotTypes()) {
                if (definition.getTypeName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(definition.getTypeName());
                }
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}