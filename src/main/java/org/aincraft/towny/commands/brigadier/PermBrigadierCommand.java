package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.commands.arguments.PlotTypeArgumentType;
import org.aincraft.towny.commands.arguments.PermissionArgumentType;
import org.aincraft.towny.commands.arguments.RoleArgumentType;
import org.aincraft.towny.commands.arguments.TownArgumentType;
import org.aincraft.towny.services.*;

import java.util.UUID;

/**
 * Brigadier implementation of the permission debugging command
 */
public class PermBrigadierCommand {

    private final TownyPlugin plugin;
    private final PermissionService permissionService;
    private final PlotService plotService;
    private final TownService townService;

    @Inject
    public PermBrigadierCommand(TownyPlugin plugin, PermissionService permissionService,
                                PlotService plotService, TownService townService) {
        this.plugin = plugin;
        this.permissionService = permissionService;
        this.plotService = plotService;
        this.townService = townService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("perm")
            .requires(source -> source.getSender().hasPermission("towny.admin.perm"))
            .executes(this::showUsage)
            .then(Commands.literal("check")
                .executes(this::checkPermissions))
            .then(Commands.literal("build")
                .executes(this::testBuildPermission))
            .then(Commands.literal("destroy")
                .executes(this::testDestroyPermission))
            .then(Commands.literal("plot")
                .executes(this::testPlotPermissionDefault)
                .then(Commands.argument("flag", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String flag : PermissionArgumentType.getAllPermissionTypes()) {
                            builder.suggest(flag);
                        }
                        return builder.buildFuture();
                    })
                    .executes(this::testPlotPermission)))
            .then(Commands.literal("town")
                .then(Commands.argument("town", TownArgumentType.town(townService))
                    .executes(this::testTownPermission)))
            .then(Commands.literal("flags")
                .executes(this::showPermissionFlags))
            .then(Commands.literal("here")
                .executes(this::showLocationInfo))
            .build();
    }

    private int showUsage(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§ePermission Test Commands:");
        sender.sendMessage("§a/perm check§f - Check all permissions at your location");
        sender.sendMessage("§a/perm build§f - Test build permission here");
        sender.sendMessage("§a/perm destroy§f - Test destroy permission here");
        sender.sendMessage("§a/perm plot [flag]§f - Test specific plot permission");
        sender.sendMessage("§a/perm town [town]§f - Test town permissions");
        sender.sendMessage("§a/perm flags§f - Show available permission flags");
        sender.sendMessage("§a/perm here§f - Show current location info");
        return Command.SINGLE_SUCCESS;
    }

    private int checkPermissions(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        player.sendMessage("§6=== Permission Check at " + x + ", " + z + " in " + world + " ===");

        boolean canBuild = permissionService.canBuild(playerUuid, x, z, world);
        boolean canDestroy = permissionService.canDestroy(playerUuid, x, z, world);
        boolean canSwitch = permissionService.canSwitch(playerUuid, x, z, world);
        boolean canUseItems = permissionService.canUseItems(playerUuid, x, z, world);

        player.sendMessage("§aBuild: " + (canBuild ? "§a✓" : "§c✗"));
        player.sendMessage("§aDestroy: " + (canDestroy ? "§a✓" : "§c✗"));
        player.sendMessage("§aSwitch: " + (canSwitch ? "§a✓" : "§c✗"));
        player.sendMessage("§aItem Use: " + (canUseItems ? "§a✓" : "§c✗"));

        return Command.SINGLE_SUCCESS;
    }

    private int testBuildPermission(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        boolean canBuild = permissionService.canBuild(playerUuid, x, z, world);

        player.sendMessage("§6Build Permission Test:");
        player.sendMessage("§fLocation: " + x + ", " + z + " in " + world);
        player.sendMessage("§fResult: " + (canBuild ? "§aALLOWED" : "§cDENIED"));

        return Command.SINGLE_SUCCESS;
    }

    private int testDestroyPermission(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        boolean canDestroy = permissionService.canDestroy(playerUuid, x, z, world);

        player.sendMessage("§6Destroy Permission Test:");
        player.sendMessage("§fLocation: " + x + ", " + z + " in " + world);
        player.sendMessage("§fResult: " + (canDestroy ? "§aALLOWED" : "§cDENIED"));

        return Command.SINGLE_SUCCESS;
    }

    private int testPlotPermissionDefault(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§cUsage: /perm plot [flag]");
        sender.sendMessage("§eUse /perm flags to see available flags");
        return 0;
    }

    private int testPlotPermission(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String flagName = StringArgumentType.getString(ctx, "flag");
        int flag = PermissionArgumentType.getFlagFromName(flagName);

        if (flag == -1) {
            player.sendMessage("§cUnknown permission flag: " + flagName);
            player.sendMessage("§eUse /perm flags to see available flags");
            return 0;
        }

        player.sendMessage("§eTesting plot permission: " + flagName);
        testBuildPermission(ctx); // Reuse existing logic for now

        return Command.SINGLE_SUCCESS;
    }

    private int testTownPermission(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String townName = TownArgumentType.getTownName(ctx, "town");
        UUID playerUuid = player.getUniqueId();

        player.sendMessage("§6Town Permission Test for " + townName + ":");

        boolean isMayor = permissionService.isTownMayor(playerUuid, townName);
        boolean isAssistant = permissionService.isTownAssistant(playerUuid, townName);
        boolean hasAdmin = permissionService.hasTownAdmin(playerUuid, townName);

        player.sendMessage("§fMayor: " + (isMayor ? "§a✓" : "§c✗"));
        player.sendMessage("§fAssistant: " + (isAssistant ? "§a✓" : "§c✗"));
        player.sendMessage("§fAdmin: " + (hasAdmin ? "§a✓" : "§c✗"));

        return Command.SINGLE_SUCCESS;
    }

    private int showPermissionFlags(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6=== Permission Flags ===");
        sender.sendMessage("§aBuild Flags:");
        sender.sendMessage("§f  BUILD (1) - Can build blocks");
        sender.sendMessage("§f  DESTROY (2) - Can break blocks");
        sender.sendMessage("§f  SWITCH (4) - Can use doors/levers/buttons");
        sender.sendMessage("§f  ITEM_USE (8) - Can use items");

        sender.sendMessage("§aTown Flags:");
        sender.sendMessage("§f  CLAIM (16) - Can claim land");
        sender.sendMessage("§f  UNCLAIM (32) - Can unclaim land");
        sender.sendMessage("§f  SPAWN (64) - Can teleport to town");
        sender.sendMessage("§f  SET_SPAWN (128) - Can set town spawn");

        sender.sendMessage("§aManagement Flags:");
        sender.sendMessage("§f  INVITE (256) - Can invite players");
        sender.sendMessage("§f  KICK (512) - Can kick players");
        sender.sendMessage("§f  PROMOTE (1024) - Can promote players");
        sender.sendMessage("§f  DEMOTE (2048) - Can demote players");

        return Command.SINGLE_SUCCESS;
    }

    private int showLocationInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String world = player.getLocation().getWorld().getName();

        player.sendMessage("§6=== Location Information ===");
        player.sendMessage("§fBlock: " + x + ", " + z);
        player.sendMessage("§fChunk: " + chunkX + ", " + chunkZ);
        player.sendMessage("§fWorld: " + world);

        // Check if in town block
        plotService.getTownBlock(chunkX, chunkZ, world).ifPresent(townBlock -> {
            player.sendMessage("§aIn Town Block!");
            player.sendMessage("§fTown ID: " + townBlock.getTownId());
            player.sendMessage("§fOwner ID: " +
                (townBlock.getOwnerId() != null ? townBlock.getOwnerId().toString() : "None (Town-owned)"));
            player.sendMessage("§fPlot Type: " + townBlock.getPlotType());
        });

        return Command.SINGLE_SUCCESS;
    }
}