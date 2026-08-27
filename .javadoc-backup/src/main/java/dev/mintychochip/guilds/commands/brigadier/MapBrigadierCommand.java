package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.flog99.mapgui.MapGui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.gui.GuildClaimScreen;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.UUID;

/**
 * Brigadier implementation of the guilds map command.
 * Opens a MapGUI map-item screen of nearby claims.
 */
public class MapBrigadierCommand {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The resident service. */
    private final ResidentService residentService;
    /** The guild service. */
    private final GuildService guildService;
    /** The plot service. */
    private final PlotService plotService;
    /** The permission service. */
    private final PermissionService permissionService;


    /**
     * Creates a new map brigadier command instance.
     * @param plugin the plugin
     * @param residentService the resident service
     * @param guildService the guild service
     * @param plotService the plot service
     * @param permissionService the permission service
     */
    public MapBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                              GuildService guildService, PlotService plotService,
                              PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.guildService = guildService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("guildsmap")
            .requires(source -> source.getSender().hasPermission("guilds.map"))
            .executes(this::handleMap)
            .then(Commands.literal("help")
                .executes(this::showHelp))
            .build();
    }

    /**
     * Handles the map.
     * @param ctx the ctx
     * @return the result
     */
    private int handleMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        if (!player.hasPermission("guilds.map")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the guilds map!");
            return 0;
        }

        if (!isMapGuiPresent()) {
            player.sendMessage(ChatColor.RED + "MapGUI is not installed. Install the MapGUI plugin to use /guildsmap.");
            return 0;
        }

        return openClaimScreen(player);
    }

    /**
     * Performs the open claim screen operation.
     * @param player the player
     * @return the result
     */
    private int openClaimScreen(Player player) {
        try {
            MapGui.get().open(player, new GuildClaimScreen(
                    getPlayerGuild(player), guildService, plotService));
            plugin.getLogger().info("MapGUI claim map opened for player: " + player.getName());
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to open map: " + e.getMessage());
            plugin.getLogger().warning("Failed to open map for player " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Kept off {@link #openClaimScreen} so the MapGui class is not resolved when the plugin is absent.
     */
    static boolean isMapGuiPresent() {
        try {
            PluginManager manager = Bukkit.getPluginManager();
            return manager != null && manager.getPlugin("MapGUI") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Performs the show help operation.
     * @param ctx the ctx
     * @return the result
     */
    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Guilds Map Help ===");
        player.sendMessage(ChatColor.WHITE + "/guildsmap" + ChatColor.GRAY + " - Open the claim map on a map item");
        player.sendMessage(ChatColor.WHITE + "/map" + ChatColor.GRAY + " - Alias for /guildsmap");
        player.sendMessage(ChatColor.WHITE + "/guildsmap help" + ChatColor.GRAY + " - Show this help message");
        player.sendMessage(ChatColor.GRAY + "Q closes the map. The map shows wilderness, your guild, other guilds, and your chunk.");

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns the player guild.
     * @param player the player
     * @return the result
     */
    private String getPlayerGuild(Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasGuild())
                .map(dev.mintychochip.guilds.models.Resident::getGuild)
                .orElse(null);
    }
}
