package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.commands.arguments.PlotTypeArgumentType;
import org.aincraft.guilds.commands.arguments.ResidentArgumentType;
import org.aincraft.guilds.commands.arguments.RoleArgumentType;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;

import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier implementation of the plot command
 */
public class PlotBrigadierCommand {

    private final JavaPlugin plugin;
    private final ResidentService residentService;
    private final GuildService guildService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final PlotTypeRegistry plotTypeRegistry;


    public PlotBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                               GuildService guildService, PlotService plotService,
                               PermissionService permissionService, PlotTypeRegistry plotTypeRegistry) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.guildService = guildService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.plotTypeRegistry = plotTypeRegistry;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("plot")
            .requires(source -> source.getSender().hasPermission("guilds.plot"))
            .executes(this::showHelp)
            // Claim subcommand
            .then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission("guilds.plot.claim"))
                .executes(this::handleClaim))
            // Unclaim subcommand
            .then(Commands.literal("unclaim")
                .requires(source -> source.getSender().hasPermission("guilds.plot.unclaim"))
                .executes(this::handleUnclaim))
            // Info subcommand
            .then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission("guilds.plot.info"))
                .executes(this::handleInfo))
            // ForSale subcommand
            .then(Commands.literal("forsale")
                .requires(source -> source.getSender().hasPermission("guilds.plot.forsale"))
                .executes(this::handleForSaleCancel)
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                    .executes(this::handleForSale)))
            // Buy subcommand
            .then(Commands.literal("buy")
                .requires(source -> source.getSender().hasPermission("guilds.plot.buy"))
                .executes(this::handleBuy))
            // Permission subcommand
            .then(Commands.literal("perm")
                .requires(source -> source.getSender().hasPermission("guilds.plot.perm"))
                .executes(this::showPermHelp)
                .then(Commands.literal("set")
                    .then(Commands.argument("role", RoleArgumentType.role())
                        .then(Commands.argument("permission", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("build");
                                builder.suggest("destroy");
                                builder.suggest("switch");
                                builder.suggest("item_use");
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("true");
                                    builder.suggest("false");
                                    return builder.buildFuture();
                                })
                                .executes(this::handlePermSet)))))
                .then(Commands.literal("list")
                    .executes(this::handlePermList)
                    .then(Commands.argument("role", RoleArgumentType.role())
                        .executes(this::handlePermRoleList))))
            // Type subcommand
            .then(Commands.literal("set")
                .requires(source -> source.getSender().hasPermission("guilds.plot.set"))
                .executes(this::showTypeHelp)
                .then(Commands.argument("type", PlotTypeArgumentType.plotType(plotTypeRegistry))
                    .executes(this::handleSetType)))
            // List subcommand
            .then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("guilds.plot.list"))
                .executes(this::handleList))
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6╔══════════════════════════════════════════════╗");
        sender.sendMessage("§6║          §e§lPLOT COMMANDS§r§6                   ║");
        sender.sendMessage("§6╠══════════════════════════════════════════════╣");
        sender.sendMessage("§6║ §f/plot claim§7                                   §6║");
        sender.sendMessage("§6║   §8» Claim the plot you're standing on         §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot unclaim§7                                 §6║");
        sender.sendMessage("§6║   §8» Unclaim the plot you're standing on       §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot info§7                                    §6║");
        sender.sendMessage("§6║   §8» Show plot information                    §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot forsale <price>§7                          §6║");
        sender.sendMessage("§6║   §8» Put plot up for sale                      §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot buy§7                                    §6║");
        sender.sendMessage("§6║   §8» Buy the plot you're standing on           §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot set <type>§7                              §6║");
        sender.sendMessage("§6║   §8» Set plot type                           §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/plot perm set <role> <perm> <value>§7           §6║");
        sender.sendMessage("§6║   §8» Set plot permissions                    §6║");
        sender.sendMessage("§6╚══════════════════════════════════════════════╝");
        return Command.SINGLE_SUCCESS;
    }

    private int handleClaim(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        // Check if plot exists and is owned by guild (not a resident)
        if (!plotService.canResidentClaimPlot(playerUuid, chunkX, chunkZ, world)) {
            player.sendMessage("§cYou cannot claim this plot. The town must claim the territory first.");
            return 0;
        }

        if (plotService.claimPlotForResident(playerUuid, chunkX, chunkZ, world)) {
            player.sendMessage("§aPlot claimed successfully!");
        } else {
            player.sendMessage("§cFailed to claim plot.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleUnclaim(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        if (!plot.isOwner(playerUuid)) {
            player.sendMessage("§cYou don't own this plot.");
            return 0;
        }

        // Unclaim plot by making it guild-owned
        Optional<GuildBlock> plotUpdate = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotUpdate.isPresent()) {
            GuildBlock existingPlot = plotUpdate.get();
            existingPlot.setOwnerId(null); // Make it guild-owned
            plotService.updateGuildBlock(existingPlot);
            player.sendMessage("§aPlot unclaimed successfully!");
        } else {
            player.sendMessage("§cFailed to unclaim plot.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return Command.SINGLE_SUCCESS;
        }

        GuildBlock plot = plotOpt.get();
        displayPlotInfo(player, plot);

        return Command.SINGLE_SUCCESS;
    }

    private int handleForSale(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        double price = DoubleArgumentType.getDouble(ctx, "price");
        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        if (!plot.isOwner(playerUuid)) {
            player.sendMessage("§cYou don't own this plot.");
            return 0;
        }

        if (plotService.setPlotForSale(plot.getId(), price, playerUuid)) {
            if (price > 0) {
                player.sendMessage("§aPlot put up for sale for §6$" + String.format("%.2f", price) + "§a!");
            } else {
                player.sendMessage("§aPlot removed from sale.");
            }
        } else {
            player.sendMessage("§cFailed to set plot for sale.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleForSaleCancel(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        if (!plot.isOwner(playerUuid)) {
            player.sendMessage("§cYou don't own this plot.");
            return 0;
        }

        if (plotService.setPlotForSale(plot.getId(), 0.0, playerUuid)) {
            player.sendMessage("§aPlot removed from sale.");
        } else {
            player.sendMessage("§cFailed to remove plot from sale.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleBuy(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        if (!plot.isForSale()) {
            player.sendMessage("§cThis plot is not for sale.");
            return 0;
        }

        if (!plotService.canResidentAffordPlot(playerUuid, plot.getId())) {
            player.sendMessage("§cYou cannot afford this plot.");
            return 0;
        }

        double price = plot.getPrice();
        if (plotService.buyPlot(playerUuid, plot.getId(), price)) {
            player.sendMessage("§aPlot purchased for §6$" + String.format("%.2f", price) + "§a!");
        } else {
            player.sendMessage("§cFailed to purchase plot.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showPermHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Plot Permission Commands ===");
        sender.sendMessage("§7/plot perm set <role> <permission> <value>§f - Set permission");
        sender.sendMessage("§7/plot perm list§f - List all permissions");
        sender.sendMessage("§7/plot perm list <role>§f - List role permissions");
        sender.sendMessage("");
        sender.sendMessage("§7Available roles: resident, ally, outsider, alliance");
        sender.sendMessage("§7Available permissions: build, destroy, switch, item_use");
        return Command.SINGLE_SUCCESS;
    }

    private int handlePermSet(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String role = RoleArgumentType.getRole(ctx, "role");
        String permission = StringArgumentType.getString(ctx, "permission").toLowerCase().replace("_", "");
        String valueStr = StringArgumentType.getString(ctx, "value").toLowerCase();
        UUID playerUuid = player.getUniqueId();

        // Convert value string to boolean
        boolean value;
        if (valueStr.equals("true") || valueStr.equals("yes") || valueStr.equals("on") || valueStr.equals("1")) {
            value = true;
        } else if (valueStr.equals("false") || valueStr.equals("no") || valueStr.equals("off") || valueStr.equals("0")) {
            value = false;
        } else {
            player.sendMessage("§cInvalid value. Use true/false, yes/no, on/off, or 1/0.");
            return 0;
        }

        // Check if player owns this plot or has admin permissions
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        if (!plot.isOwner(playerUuid) && !permissionService.hasPermission(playerUuid, "plot_owner", "plot", plot.getGuildId())) {
            player.sendMessage("§cYou don't own this plot and don't have permission to modify it.");
            return 0;
        }

        try {
            // Update plot permissions (simplified implementation)
            int currentPerms = plot.getPermissionsFlags();
            int newPerms = updatePermissionFlags(currentPerms, role, permission, value);
            plot.setPermissionsFlags(newPerms);
            plotService.updateGuildBlock(plot);
            player.sendMessage("§aSet " + role + " " + permission + " permission to " + (value ? "§atrue" : "§cfalse") + "§a.");
        } catch (Exception e) {
            player.sendMessage("§cFailed to set plot permission: " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handlePermList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        displayPlotPermissions(player, plot, null);

        return Command.SINGLE_SUCCESS;
    }

    private int handlePermRoleList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String role = RoleArgumentType.getRole(ctx, "role");
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();
        displayPlotPermissions(player, plot, role);

        return Command.SINGLE_SUCCESS;
    }

    private int showTypeHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Plot Type Commands ===");
        sender.sendMessage("§7/plot set <type>§f - Set plot type");
        sender.sendMessage("");
        sender.sendMessage("§7Available plot types:");

        // Dynamically list all registered plot types
        var plotTypes = plotTypeRegistry.getAllPlotTypes();
        for (var plotType : plotTypes) {
            if (plotType.isEnabled()) {
                sender.sendMessage("§f  " + plotType.getTypeName() + "§7 - " + plotType.getDescription());
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetType(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String plotType = PlotTypeArgumentType.getPlotType(ctx, "type");
        UUID playerUuid = player.getUniqueId();

        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        Optional<GuildBlock> plotOpt = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (plotOpt.isEmpty()) {
            player.sendMessage("§cNo plot found at this location.");
            return 0;
        }

        GuildBlock plot = plotOpt.get();

        // Check if player is plot owner, guild admin, or has plot_set permission
        var guildOpt = guildService.getGuildById(plot.getGuildId());
        boolean isGuildAdmin = guildOpt.isPresent() && permissionService.hasGuildAdmin(playerUuid, guildOpt.get().getName());
        boolean isPlotOwner = plot.isOwner(playerUuid);
        boolean hasPermission = permissionService.hasPermission(playerUuid, "plot_set", "plot", plot.getGuildId());

        if (!isPlotOwner && !isGuildAdmin && !hasPermission) {
            player.sendMessage("§cYou don't have permission to modify this plot.");
            return 0;
        }

        try {
            plotService.setPlotType(plot.getId(), plotType);
            player.sendMessage("§aPlot type set to §e" + plotType + "§a!");
        } catch (Exception e) {
            player.sendMessage("§cFailed to set plot type: " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        player.sendMessage("§eThis command is not yet implemented. Plot listing will be available in a future update.");

        return Command.SINGLE_SUCCESS;
    }

    private void displayPlotInfo(org.bukkit.entity.Player player, GuildBlock plot) {
        player.sendMessage("§6=== Plot Information ===");
        player.sendMessage("§fType: §a" + plot.getPlotType());
        player.sendMessage("§fChunk: §7[" + plot.getX() + ", " + plot.getZ() + "]");
        player.sendMessage("§fWorld: §7" + plot.getWorld());

        if (plot.getOwnerId() != null) {
            // Try to get owner name
            try {
                String ownerName = org.bukkit.Bukkit.getOfflinePlayer(plot.getOwnerId()).getName();
                if (ownerName != null) {
                    player.sendMessage("§fOwner: §a" + ownerName);
                } else {
                    player.sendMessage("§fOwner: §7" + plot.getOwnerId());
                }
            } catch (Exception e) {
                player.sendMessage("§fOwner: §7" + plot.getOwnerId());
            }
        } else {
            player.sendMessage("§fOwner: §7Town-owned");
        }

        player.sendMessage("§fTown: §a" + plot.getGuildId());

        if (plot.isForSale()) {
            player.sendMessage("§fPrice: §6$" + String.format("%.2f", plot.getPrice()));
        }

        // Show permissions
        displayPlotPermissions(player, plot, null);
    }

    private void displayPlotPermissions(org.bukkit.entity.Player player, GuildBlock plot, String specificRole) {
        int permissions = plot.getPermissionsFlags();

        if (specificRole != null) {
            // Show permissions for specific role
            boolean canBuild = (permissions & getPermissionFlag("build", specificRole)) != 0;
            boolean canDestroy = (permissions & getPermissionFlag("destroy", specificRole)) != 0;
            boolean canSwitch = (permissions & getPermissionFlag("switch", specificRole)) != 0;
            boolean canUseItems = (permissions & getPermissionFlag("item_use", specificRole)) != 0;

            player.sendMessage("§6=== " + specificRole.toUpperCase() + " Permissions ===");
            player.sendMessage("§fBuild: " + (canBuild ? "§aYes" : "§cNo"));
            player.sendMessage("§fDestroy: " + (canDestroy ? "§aYes" : "§cNo"));
            player.sendMessage("§fSwitch: " + (canSwitch ? "§aYes" : "§cNo"));
            player.sendMessage("§fItem Use: " + (canUseItems ? "§aYes" : "§cNo"));
        } else {
            // Show all role permissions
            String[] roles = {"resident", "ally", "outsider", "alliance"};
            for (String role : roles) {
                boolean canBuild = (permissions & getPermissionFlag("build", role)) != 0;
                boolean canDestroy = (permissions & getPermissionFlag("destroy", role)) != 0;
                boolean canSwitch = (permissions & getPermissionFlag("switch", role)) != 0;
                boolean canUseItems = (permissions & getPermissionFlag("item_use", role)) != 0;

                player.sendMessage("§6" + role.toUpperCase() + ": §f" +
                                 (canBuild ? "§aB" : "§c-") + " " +
                                 (canDestroy ? "§aD" : "§c-") + " " +
                                 (canSwitch ? "§aS" : "§c-") + " " +
                                 (canUseItems ? "§aI" : "§c-"));
            }
            player.sendMessage("§7Legend: §aB§7=Build, §aD§7=Destroy, §aS§7=Switch, §aI§7=Item Use");
        }
    }

    private int updatePermissionFlags(int currentFlags, String role, String permission, boolean value) {
        int flag = getPermissionFlag(permission, role);
        if (value) {
            return currentFlags | flag; // Set the flag
        } else {
            return currentFlags & ~flag; // Clear the flag
        }
    }

    private int getPermissionFlag(String permission, String role) {
        // Simplified permission flag calculation
        // In a real implementation, this would use the actual Permission class
        int roleShift = getRoleShift(role);
        int permShift = getPermissionShift(permission);
        return (1 << (roleShift + permShift));
    }

    private int getRoleShift(String role) {
        switch (role.toLowerCase()) {
            case "resident": return 0;
            case "ally": return 4;
            case "outsider": return 8;
            case "alliance": return 12;
            default: return 0;
        }
    }

    private int getPermissionShift(String permission) {
        switch (permission.toLowerCase()) {
            case "build": return 0;
            case "destroy": return 1;
            case "switch": return 2;
            case "item_use": return 3;
            default: return 0;
        }
    }
}