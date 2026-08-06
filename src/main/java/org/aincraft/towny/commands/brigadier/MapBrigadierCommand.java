package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.utils.MapRenderer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/**
 * Brigadier implementation of the towny map command
 */
public class MapBrigadierCommand {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final MapRenderer mapRenderer;

    @Inject
    public MapBrigadierCommand(TownyPlugin plugin, ResidentService residentService,
                              TownService townService, PlotService plotService,
                              PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.mapRenderer = new MapRenderer(townService, plotService);
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("townymap")
            .requires(source -> source.getSender().hasPermission("towny.map"))
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

        if (!player.hasPermission("towny.map")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the towny map!");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String world = player.getLocation().getWorld().getName();

        try {
            var mapLines = mapRenderer.renderMap(playerChunkX, playerChunkZ, world, playerTown);

            for (String line : mapLines) {
                player.sendMessage(line);
            }

            String areaSummary = mapRenderer.getAreaSummary(playerChunkX, playerChunkZ, world, playerTown);
            player.sendMessage(areaSummary);

            plugin.getLogger().info("Map displayed for player: " + player.getName() + " at (" + playerChunkX + ", " + playerChunkZ + ")");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to render map: " + e.getMessage());
            plugin.getLogger().warning("Failed to render map for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleCompactMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        if (!player.hasPermission("towny.map")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the towny map!");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String world = player.getLocation().getWorld().getName();

        try {
            var mapLines = mapRenderer.renderCompactMap(playerChunkX, playerChunkZ, world, playerTown);

            for (String line : mapLines) {
                player.sendMessage(line);
            }

            String areaSummary = mapRenderer.getAreaSummary(playerChunkX, playerChunkZ, world, playerTown);
            player.sendMessage(areaSummary);

            plugin.getLogger().info("Compact map displayed for player: " + player.getName());

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to render compact map: " + e.getMessage());
            plugin.getLogger().warning("Failed to render compact map for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleCoordsMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        int targetX = IntegerArgumentType.getInteger(ctx, "x");
        int targetZ = IntegerArgumentType.getInteger(ctx, "z");
        String playerTown = getPlayerTown(player);
        String world = player.getLocation().getWorld().getName();

        try {
            var mapLines = mapRenderer.renderMap(targetX, targetZ, world, playerTown);

            player.sendMessage(ChatColor.YELLOW + "=== Map at coordinates (" + targetX + ", " + targetZ + ") ===");

            for (String line : mapLines) {
                player.sendMessage(line);
            }

            plugin.getLogger().info("Coordinates map displayed for player: " + player.getName() + " at (" + targetX + ", " + targetZ + ")");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to render map: " + e.getMessage());
            plugin.getLogger().warning("Failed to render coordinates map for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return 0;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Towny Map Help ===");

        player.sendMessage(ChatColor.WHITE + "/townymap" + ChatColor.GRAY + " - Show full map centered on your location");
        player.sendMessage(ChatColor.WHITE + "/townymap compact" + ChatColor.GRAY + " - Show compact map (7x7 chunks)");
        player.sendMessage(ChatColor.WHITE + "/townymap small" + ChatColor.GRAY + " - Show compact map (7x7 chunks)");
        player.sendMessage(ChatColor.WHITE + "/townymap big" + ChatColor.GRAY + " - Show full map (11x11 chunks)");
        player.sendMessage(ChatColor.WHITE + "/townymap here <x> <z>" + ChatColor.GRAY + " - Show map at specific coordinates");
        player.sendMessage(ChatColor.WHITE + "/townymap help" + ChatColor.GRAY + " - Show this help message");

        player.sendMessage(ChatColor.GRAY + "");
        player.sendMessage(ChatColor.GRAY + "Map Legend:");
        player.sendMessage(ChatColor.GREEN + "o" + ChatColor.GRAY + " - Your location");
        player.sendMessage(ChatColor.DARK_GREEN + "-" + ChatColor.GRAY + " - Wilderness (unclaimed)");
        player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GRAY + " - Your town's blocks");
        player.sendMessage(ChatColor.YELLOW + "+" + ChatColor.GRAY + " - Other town blocks");
        player.sendMessage(ChatColor.AQUA + "+" + ChatColor.GRAY + " - Personally owned plot");
        player.sendMessage(ChatColor.GOLD + "+" + ChatColor.GRAY + " - Shop plot");
        player.sendMessage(ChatColor.RED + "+" + ChatColor.GRAY + " - Bank plot");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "+" + ChatColor.GRAY + " - Inn/Embassy plot");

        return Command.SINGLE_SUCCESS;
    }

    private String getPlayerTown(Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);
    }
}