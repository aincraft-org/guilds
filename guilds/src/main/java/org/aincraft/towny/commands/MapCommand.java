package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.utils.MapRenderer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Towny map command handler
 */
public class MapCommand extends TownyCommand {

    private final MapRenderer mapRenderer;

    @Inject
    public MapCommand(TownyPlugin plugin, ResidentService residentService, TownService townService,
                     PlotService plotService, PermissionService permissionService) {
        super(plugin, residentService, townService, plotService, permissionService);
        this.mapRenderer = new MapRenderer(townService, plotService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = getPlayer(sender);
        if (!requirePlayer(sender, player)) {
            return true;
        }

        // Check if player has permission
        if (!player.hasPermission("towny.map")) {
            sendError(player, "You don't have permission to use the towny map!");
            return true;
        }

        // Handle different map modes
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "compact":
                case "small":
                case "quick":
                    handleCompactMap(player);
                    return true;
                case "big":
                case "large":
                case "full":
                    handleFullMap(player);
                    return true;
                case "here":
                case "coords":
                    handleCoordsMap(player, args);
                    return true;
                case "help":
                    showHelp(player);
                    return true;
                default:
                    // If it's not a recognized mode, show help
                    sendError(player, "Unknown map mode: " + mode);
                    showHelp(player);
                    return true;
            }
        } else {
            // Default: show full map
            handleFullMap(player);
        }

        return true;
    }

    /**
     * Handle the default full map display
     * @param player Player to show map to
     */
    private void handleFullMap(Player player) {
        String playerTown = getPlayerTown(player);
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String world = player.getLocation().getWorld().getName();

        try {
            List<String> mapLines = mapRenderer.renderMap(playerChunkX, playerChunkZ, world, playerTown);

            // Send the map to the player
            for (String line : mapLines) {
                player.sendMessage(line);
            }

            // Send area summary
            String areaSummary = mapRenderer.getAreaSummary(playerChunkX, playerChunkZ, world, playerTown);
            player.sendMessage(areaSummary);

            logInfo("Map displayed for player: " + player.getName() + " at (" + playerChunkX + ", " + playerChunkZ + ")");

        } catch (Exception e) {
            sendError(player, "Failed to render map: " + e.getMessage());
            logWarning("Failed to render map for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle compact map display
     * @param player Player to show map to
     */
    private void handleCompactMap(Player player) {
        String playerTown = getPlayerTown(player);
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String world = player.getLocation().getWorld().getName();

        try {
            List<String> mapLines = mapRenderer.renderCompactMap(playerChunkX, playerChunkZ, world, playerTown);

            // Send the compact map to the player
            for (String line : mapLines) {
                player.sendMessage(line);
            }

            // Send area summary
            String areaSummary = mapRenderer.getAreaSummary(playerChunkX, playerChunkZ, world, playerTown);
            player.sendMessage(areaSummary);

            logInfo("Compact map displayed for player: " + player.getName());

        } catch (Exception e) {
            sendError(player, "Failed to render compact map: " + e.getMessage());
            logWarning("Failed to render compact map for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle coordinates-based map display
     * @param player Player to show map to
     * @param args Command arguments containing coordinates
     */
    private void handleCoordsMap(Player player, String[] args) {
        if (args.length < 3) {
            sendError(player, "Usage: /towny map here <x> <z>");
            sendInfo(player, "Example: /towny map here 100 -200");
            return;
        }

        try {
            int targetX = Integer.parseInt(args[1]);
            int targetZ = Integer.parseInt(args[2]);
            String playerTown = getPlayerTown(player);
            String world = player.getLocation().getWorld().getName();

            List<String> mapLines = mapRenderer.renderMap(targetX, targetZ, world, playerTown);

            player.sendMessage(ChatColor.YELLOW + "=== Map at coordinates (" + targetX + ", " + targetZ + ") ===");

            for (String line : mapLines) {
                player.sendMessage(line);
            }

            logInfo("Coordinates map displayed for player: " + player.getName() + " at (" + targetX + ", " + targetZ + ")");

        } catch (NumberFormatException e) {
            sendError(player, "Invalid coordinates! Please use integer values for X and Z.");
        } catch (Exception e) {
            sendError(player, "Failed to render map: " + e.getMessage());
            logWarning("Failed to render coordinates map for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Show help for the map command
     * @param player Player to show help to
     */
    private void showHelp(Player player) {
        sendHelpHeader(player, "Towny Map Help");

        sendHelpLine(player, "/towny map", "Show full map centered on your location");
        sendHelpLine(player, "/towny map compact", "Show compact map (7x7 chunks)");
        sendHelpLine(player, "/towny map small", "Show compact map (7x7 chunks)");
        sendHelpLine(player, "/towny map big", "Show full map (11x11 chunks)");
        sendHelpLine(player, "/towny map here <x> <z>", "Show map at specific coordinates");
        sendHelpLine(player, "/towny map help", "Show this help message");

        sendSecondary(player, "");
        sendSecondary(player, "Map Legend:");
        player.sendMessage(ChatColor.GREEN + "o" + ChatColor.GRAY + " - Your location");
        player.sendMessage(ChatColor.DARK_GREEN + "-" + ChatColor.GRAY + " - Wilderness (unclaimed)");
        player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GRAY + " - Your town's blocks");
        player.sendMessage(ChatColor.YELLOW + "+" + ChatColor.GRAY + " - Other town blocks");
        player.sendMessage(ChatColor.AQUA + "+" + ChatColor.GRAY + " - Personally owned plot");
        player.sendMessage(ChatColor.GOLD + "+" + ChatColor.GRAY + " - Shop plot");
        player.sendMessage(ChatColor.RED + "+" + ChatColor.GRAY + " - Bank plot");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "+" + ChatColor.GRAY + " - Inn/Embassy plot");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = Arrays.asList("compact", "small", "big", "large", "full", "here", "coords", "help");

        if (args.length == 1) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // For "here" command, we could suggest common coordinates
        if (args.length == 2 && "here".equalsIgnoreCase(args[0])) {
            return Arrays.asList("0", "100", "-100", "1000", "-1000").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && "here".equalsIgnoreCase(args[0])) {
            return Arrays.asList("0", "100", "-100", "1000", "-1000").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return null;
    }
}