package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.flog99.mapgui.MapGui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.guilds.gui.GuildClaimScreen;
import org.aincraft.guilds.gui.MapFollowTask;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Brigadier implementation of guild map subcommands.
 */
public class MapBrigadierCommand {

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final PlotService plotService;
    private final ResidentService residentService;
    private final PermissionService permissionService;

    public MapBrigadierCommand(JavaPlugin plugin, GuildService guildService, PlotService plotService,
                               ResidentService residentService, PermissionService permissionService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.plotService = plotService;
        this.residentService = residentService;
        this.permissionService = permissionService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("map")
            .requires(source -> source.getSender().hasPermission("guilds.map"))
            .executes(this::handleFullMap)
            .then(Commands.literal("compact")
                .executes(this::handleCompactMap))
            .then(Commands.literal("small")
                .executes(this::handleCompactMap))
            .then(Commands.literal("big")
                .executes(this::handleFullMap))
            .then(Commands.literal("large")
                .executes(this::handleFullMap))
            .then(Commands.literal("full")
                .executes(this::handleFullMap))
            .then(Commands.literal("here")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(this::handleCoordsMap))))
            .then(Commands.literal("help")
                .executes(this::showHelp))
            .build();
    }

    private int handleFullMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        if (!player.hasPermission("guilds.map")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the guilds map!");
            return 0;
        }

        return openMap(player);
    }

    private int handleCompactMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        if (!player.hasPermission("guilds.map")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the guilds map!");
            return 0;
        }

        return openMap(player);
    }

    private int handleCoordsMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        return openMap(player);
    }

    private int openMap(Player player) {
        String playerGuild = getPlayerGuild(player);
        try {
            MapFollowTask.start(plugin, MapGui.get());
            MapGui.get().open(player, new GuildClaimScreen(playerGuild, guildService, plotService, permissionService));
            plugin.getLogger().info("MapGUI claim map opened for player: " + player.getName());
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to open map: " + e.getMessage());
            plugin.getLogger().warning("Failed to open MapGUI map for " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Guilds Map Help ===");

        player.sendMessage(ChatColor.WHITE + "/guild map" + ChatColor.GRAY + " - Show full map centered on your location");
        player.sendMessage(ChatColor.WHITE + "/guild map compact" + ChatColor.GRAY + " - Show compact map (7x7 chunks)");
        player.sendMessage(ChatColor.WHITE + "/guild map small" + ChatColor.GRAY + " - Show compact map (7x7 chunks)");
        player.sendMessage(ChatColor.WHITE + "/guild map big" + ChatColor.GRAY + " - Show full map (11x11 chunks)");
        player.sendMessage(ChatColor.WHITE + "/guild map here <x> <z>" + ChatColor.GRAY + " - Show map at specific coordinates");
        player.sendMessage(ChatColor.WHITE + "/guild map help" + ChatColor.GRAY + " - Show this help message");
        player.sendMessage(ChatColor.GRAY + "Aliases: /guilds map, /g map");

        player.sendMessage(ChatColor.GRAY + "");
        player.sendMessage(ChatColor.GRAY + "Map Legend:");
        player.sendMessage(ChatColor.GREEN + "o" + ChatColor.GRAY + " - Your location");
        player.sendMessage(ChatColor.DARK_GREEN + "-" + ChatColor.GRAY + " - Wilderness (unclaimed)");
        player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GRAY + " - Your guild's blocks");
        player.sendMessage(ChatColor.YELLOW + "+" + ChatColor.GRAY + " - Other guild blocks");
        player.sendMessage(ChatColor.AQUA + "+" + ChatColor.GRAY + " - Personally owned plot");
        player.sendMessage(ChatColor.GOLD + "+" + ChatColor.GRAY + " - Shop plot");
        player.sendMessage(ChatColor.RED + "+" + ChatColor.GRAY + " - Bank plot");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "+" + ChatColor.GRAY + " - Inn/Embassy plot");

        return Command.SINGLE_SUCCESS;
    }

    private String getPlayerGuild(Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasGuild())
                .map(org.aincraft.guilds.models.Resident::getGuild)
                .orElse(null);
    }
}
