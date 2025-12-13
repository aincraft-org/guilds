package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Base class for Towny commands with common functionality and dependency injection
 */
public abstract class TownyCommand implements CommandExecutor, TabCompleter {

    protected final TownyPlugin plugin;
    protected final ResidentService residentService;
    protected final TownService townService;
    protected final PlotService plotService;
    protected final PermissionService permissionService;

    @Inject
    public TownyCommand(TownyPlugin plugin, ResidentService residentService,
                       TownService townService, PlotService plotService,
                       PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    /**
     * Check if the command sender is a player
     * @param sender Command sender
     * @return Player if sender is a player, null otherwise
     */
    protected Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }

    /**
     * Send a message to a player with proper formatting
     * @param player Player to send message to
     * @param color Color of the message
     * @param message Message content
     */
    protected void sendMessage(Player player, ChatColor color, String message) {
        player.sendMessage(color + message);
    }

    /**
     * Send an error message to a player
     * @param player Player to send error to
     * @param message Error message
     */
    protected void sendError(Player player, String message) {
        player.sendMessage(ChatColor.RED + message);
    }

    /**
     * Send a success message to a player
     * @param player Player to send success to
     * @param message Success message
     */
    protected void sendSuccess(Player player, String message) {
        player.sendMessage(ChatColor.GREEN + message);
    }

    /**
     * Send an info message to a player
     * @param player Player to send info to
     * @param message Info message
     */
    protected void sendInfo(Player player, String message) {
        player.sendMessage(ChatColor.YELLOW + message);
    }

    /**
     * Send a gray secondary message to a player
     * @param player Player to send message to
     * @param message Message content
     */
    protected void sendSecondary(Player player, String message) {
        player.sendMessage(ChatColor.GRAY + message);
    }

    /**
     * Get the town of a player
     * @param player Player to get town for
     * @return Town name or null if no town
     */
    protected String getPlayerTown(Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);
    }

    /**
     * Check if a player is in a town
     * @param player Player to check
     * @return True if player is in a town
     */
    protected boolean isPlayerInTown(Player player) {
        return getPlayerTown(player) != null;
    }

    /**
     * Validate that a player is in a town and send error message if not
     * @param player Player to validate
     * @return True if player is in a town
     */
    protected boolean requirePlayerInTown(Player player) {
        if (!isPlayerInTown(player)) {
            sendError(player, "You are not in a town!");
            return false;
        }
        return true;
    }

    /**
     * Validate that a command sender is a player and send error message if not
     * @param sender Command sender
     * @param player Player instance if sender is a player
     * @return True if sender is a player
     */
    protected boolean requirePlayer(CommandSender sender, Player player) {
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return false;
        }
        return true;
    }

    /**
     * Send help header with given title
     * @param player Player to send help to
     * @param title Help title
     */
    protected void sendHelpHeader(Player player, String title) {
        player.sendMessage(ChatColor.YELLOW + "=== " + title + " ===");
    }

    /**
     * Send a command help line
     * @param player Player to send help to
     * @param command Command syntax
     * @param description Command description
     */
    protected void sendHelpLine(Player player, String command, String description) {
        player.sendMessage(ChatColor.WHITE + command + " " + ChatColor.GRAY + "- " + description);
    }

    /**
     * Log an info message to the plugin logger
     * @param message Message to log
     */
    protected void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    /**
     * Log a warning message to the plugin logger
     * @param message Message to log
     */
    protected void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    /**
     * Log a severe message to the plugin logger
     * @param message Message to log
     */
    protected void logSevere(String message) {
        plugin.getLogger().severe(message);
    }
}