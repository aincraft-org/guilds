package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * General Towny command handler for the main /towny command
 */
public class TownyGeneralCommand extends TownyCommand {

    private final MapCommand mapCommand;

    @Inject
    public TownyGeneralCommand(TownyPlugin plugin, ResidentService residentService, TownService townService,
                              PlotService plotService, PermissionService permissionService, MapCommand mapCommand) {
        super(plugin, residentService, townService, plotService, permissionService);
        this.mapCommand = mapCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = getPlayer(sender);
        if (!requirePlayer(sender, player)) {
            return true;
        }

        // Handle different subcommands
        if (args.length == 0) {
            showTownyInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "map":
                // Delegate to MapCommand logic
                handleMapCommand(player, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "chat":
            case "tc":
                handleTownChat(player, args);
                break;
            case "top":
                handleTopCommand(player, args);
                break;
            case "prices":
                handlePricesCommand(player);
                break;
            case "time":
                handleTimeCommand(player);
                break;
            case "universe":
                handleUniverseCommand(player);
                break;
            case "version":
                handleVersionCommand(player);
                break;
            case "help":
                showHelp(player);
                break;
            default:
                sendError(player, "Unknown command: " + subCommand);
                sendInfo(player, "Use '/towny help' for available commands.");
                break;
        }

        return true;
    }

    /**
     * Show general Towny information
     * @param player Player to show info to
     */
    private void showTownyInfo(Player player) {
        sendHelpHeader(player, "Towny Plugin Information");

        sendInfo(player, "Welcome to Towny v" + plugin.getDescription().getVersion());
        sendSecondary(player, "A town and nation management plugin");
        sendSecondary(player, "Website: " + plugin.getDescription().getWebsite());
        sendSecondary(player, "");

        // Show player's current status
        String playerTown = getPlayerTown(player);
        if (playerTown != null) {
            sendInfo(player, "You are a resident of: " + ChatColor.AQUA + playerTown);
        } else {
            sendSecondary(player, "You are not currently in a town");
            sendSecondary(player, "Use '/town create <name>' to start a town");
            sendSecondary(player, "Or '/town join <name>' to join an existing town");
        }

        sendSecondary(player, "");
        sendSecondary(player, "Use '/towny help' for available commands");
    }

    /**
     * Handle map command delegation
     * @param player Player
     * @param args Arguments for map command
     */
    private void handleMapCommand(Player player, String[] args) {
        // Delegate to MapCommand
        mapCommand.onCommand(player, null, "map", args);
    }

    /**
     * Handle top towns ranking command
     * @param player Player
     * @param args Command arguments
     */
    private void handleTopCommand(Player player, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "residents";

        sendHelpHeader(player, "Top Towns by " + type);

        try {
            switch (type) {
                case "residents":
                case "population":
                    showTopTownsByResidents(player);
                    break;
                case "balance":
                case "money":
                case "wealth":
                    showTopTownsByBalance(player);
                    break;
                case "land":
                case "claims":
                    showTopTownsByLand(player);
                    break;
                default:
                    sendError(player, "Unknown ranking type: " + type);
                    sendSecondary(player, "Available types: residents, balance, land");
                    break;
            }
        } catch (Exception e) {
            sendError(player, "Failed to retrieve town rankings: " + e.getMessage());
            logWarning("Failed to get town rankings: " + e.getMessage());
        }
    }

    /**
     * Show top towns by resident count
     * @param player Player
     */
    private void showTopTownsByResidents(Player player) {
        List<org.aincraft.towny.models.Town> towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            sendSecondary(player, "No towns found.");
            return;
        }

        // Sort by resident count (top 10)
        towns.sort((t1, t2) -> Integer.compare(t2.getResidentCount(), t1.getResidentCount()));

        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            org.aincraft.towny.models.Town town = towns.get(i);
            player.sendMessage(ChatColor.WHITE + String.valueOf(i + 1) + ". " + ChatColor.AQUA + town.getName() +
                             ChatColor.GRAY + " (" + town.getResidentCount() + " residents)");
        }
    }

    /**
     * Show top towns by balance
     * @param player Player
     */
    private void showTopTownsByBalance(Player player) {
        List<org.aincraft.towny.models.Town> towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            sendSecondary(player, "No towns found.");
            return;
        }

        // Sort by balance (top 10)
        towns.sort((t1, t2) -> Double.compare(t2.getBalance(), t1.getBalance()));

        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            org.aincraft.towny.models.Town town = towns.get(i);
            player.sendMessage(ChatColor.WHITE + String.valueOf(i + 1) + ". " + ChatColor.AQUA + town.getName() +
                             ChatColor.GOLD + " §" + String.format("%.2f", town.getBalance()));
        }
    }

    /**
     * Show top towns by land claimed
     * @param player Player
     */
    private void showTopTownsByLand(Player player) {
        // This would require additional service method to count town blocks
        // For now, show a placeholder message
        sendSecondary(player, "Land ranking functionality coming soon!");
        sendSecondary(player, "This will show towns ranked by number of claimed chunks.");
    }

    /**
     * Show Towny prices information
     * @param player Player
     */
    private void handlePricesCommand(Player player) {
        sendHelpHeader(player, "Towny Prices");

        sendInfo(player, "Town Creation: " + ChatColor.GOLD + "§" + "500");
        sendInfo(player, "Town Claim: " + ChatColor.GOLD + "§" + "25");
        sendInfo(player, "Outpost Claim: " + ChatColor.GOLD + "§" + "100");
        sendInfo(player, "Plot Claim: " + ChatColor.GOLD + "§" + "10");

        sendSecondary(player, "");
        sendInfo(player, "Town Upkeep: " + ChatColor.GOLD + "§" + "10/day");
        sendInfo(player, "Nation Upkeep: " + ChatColor.GOLD + "§" + "100/day");

        sendSecondary(player, "");
        sendSecondary(player, "* Prices are configurable by server administrators");
    }

    /**
     * Show time until next tax collection
     * @param player Player
     */
    private void handleTimeCommand(Player player) {
        sendHelpHeader(player, "Towny Time Information");

        // This would calculate actual time until next tax day
        // For now, show placeholder information
        long hoursUntilTax = 24; // Placeholder
        sendInfo(player, "Next tax collection in: " + ChatColor.AQUA + hoursUntilTax + " hours");
        sendSecondary(player, "New day starts at midnight server time");
    }

    /**
     * Show universe statistics
     * @param player Player
     */
    private void handleUniverseCommand(Player player) {
        sendHelpHeader(player, "Towny Universe Statistics");

        try {
            int townCount = townService.getAllTowns().size();
            int residentCount = 0; // Would need service method to get total residents

            sendInfo(player, "Towns: " + ChatColor.AQUA + townCount);
            sendInfo(player, "Residents: " + ChatColor.AQUA + residentCount);
            sendInfo(player, "World: " + ChatColor.AQUA + player.getLocation().getWorld().getName());

            sendSecondary(player, "");
            sendSecondary(player, "More detailed statistics coming soon!");

        } catch (Exception e) {
            sendError(player, "Failed to retrieve universe statistics: " + e.getMessage());
        }
    }

    /**
     * Show plugin version information
     * @param player Player
     */
    private void handleVersionCommand(Player player) {
        sendHelpHeader(player, "Towny Version Information");

        sendInfo(player, "Version: " + ChatColor.AQUA + plugin.getDescription().getVersion());
        sendInfo(player, "Author: " + ChatColor.AQUA + plugin.getDescription().getAuthors());
        sendInfo(player, "Website: " + ChatColor.AQUA + plugin.getDescription().getWebsite());

        sendSecondary(player, "");
        sendSecondary(player, "Minecraft Version: " + ChatColor.AQUA + plugin.getDescription().getAPIVersion());
        sendSecondary(player, "Java Version: " + ChatColor.AQUA + System.getProperty("java.version"));
    }

    /**
     * Handle town chat command
     * @param player Player
     * @param args Command arguments
     */
    private void handleTownChat(Player player, String[] args) {
        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            sendSecondary(player, "Use '/town create <name>' to start a town or '/town join <name>' to join one.");
            return;
        }

        if (args.length < 2) {
            sendError(player, "Usage: /towny chat <message>");
            sendSecondary(player, "Sends a message to all residents of your town.");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        sendTownChatMessage(player, playerTown, message);
    }

    /**
     * Send a chat message to all town residents
     * @param sender Player sending the message
     * @param townName Town to send message to
     * @param message Chat message
     */
    private void sendTownChatMessage(Player sender, String townName, String message) {
        String formattedMessage = ChatColor.AQUA + "[TC] " + ChatColor.WHITE + sender.getName() +
                                ChatColor.GRAY + ": " + ChatColor.RESET + message;

        try {
            // Get all residents of the town
            var residents = residentService.getResidentsInTown(townName);

            // Send message to all online residents
            for (org.bukkit.entity.Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                UUID onlineUuid = onlinePlayer.getUniqueId();

                // Check if this online player is a resident of the town
                boolean isResident = residents.stream()
                    .anyMatch(resident -> resident.getUuid().equals(onlineUuid));

                if (isResident) {
                    onlinePlayer.sendMessage(formattedMessage);
                }
            }

            // Log the message
            plugin.getLogger().info("Town Chat [" + townName + "] " + sender.getName() + ": " + message);

        } catch (Exception e) {
            sendError(sender, "Failed to send town chat message: " + e.getMessage());
            logWarning("Error sending town chat: " + e.getMessage());
        }
    }

    /**
     * Show general Towny help
     * @param player Player
     */
    private void showHelp(Player player) {
        sendHelpHeader(player, "Towny Commands");

        sendHelpLine(player, "/towny", "Show general Towny information");
        sendHelpLine(player, "/towny map [mode]", "Show towny map (compact, big, here)");
        sendHelpLine(player, "/towny chat <msg>", "Send message to your town");
        sendHelpLine(player, "/towny top [type]", "Show top towns (residents, balance, land)");
        sendHelpLine(player, "/towny prices", "Show costs for various actions");
        sendHelpLine(player, "/towny time", "Show time until next tax collection");
        sendHelpLine(player, "/towny universe", "Show server statistics");
        sendHelpLine(player, "/towny version", "Show plugin version information");

        sendSecondary(player, "");
        sendHelpHeader(player, "Related Commands");
        sendHelpLine(player, "/town", "Town management commands");
        sendHelpLine(player, "/resident", "Resident commands");
        sendHelpLine(player, "/plot", "Plot management commands");

        sendSecondary(player, "");
        sendSecondary(player, "Use '/town help', '/resident help', or '/plot help' for more specific commands.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = Arrays.asList("map", "chat", "tc", "top", "prices", "time", "universe", "version", "help");

        if (args.length == 1) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "top".equals(args[0].toLowerCase())) {
            return Arrays.asList("residents", "balance", "land").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "map".equals(args[0].toLowerCase())) {
            return Arrays.asList("compact", "small", "big", "large", "full", "here").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return null;
    }
}