package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;

import java.util.UUID;

/**
 * Brigadier implementation of the guilds general command
 */
public class GuildsGeneralBrigadierCommand {

    private final JavaPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;


    public GuildsGeneralBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                                       TownService townService, PlotService plotService,
                                       PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("guilds")
            .requires(source -> source.getSender().hasPermission("guilds.general"))
            .executes(this::showHelp)
            // Version subcommand
            .then(Commands.literal("version")
                .requires(source -> source.getSender().hasPermission("guilds.general.version"))
                .executes(this::handleVersion))
            // Time subcommand
            .then(Commands.literal("time")
                .requires(source -> source.getSender().hasPermission("guilds.general.time"))
                .executes(this::handleTime))
            // Top subcommand
            .then(Commands.literal("top")
                .requires(source -> source.getSender().hasPermission("guilds.general.top"))
                .executes(this::showTopHelp)
                .then(Commands.literal("residents")
                    .executes(this::handleTopResidents))
                .then(Commands.literal("towns")
                    .executes(this::handleTopTowns))
                .then(Commands.literal("land")
                    .executes(this::handleTopLand)))
            // Prices subcommand
            .then(Commands.literal("prices")
                .requires(source -> source.getSender().hasPermission("guilds.general.prices"))
                .executes(this::handlePrices))
            // Chat subcommand
            .then(Commands.literal("chat")
                .requires(source -> source.getSender().hasPermission("guilds.general.chat"))
                .executes(this::handleChatHelp)
                .then(Commands.literal("tc")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::handleTownChat)))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::handleTownChat)))
            // Universe subcommand
            .then(Commands.literal("universe")
                .requires(source -> source.getSender().hasPermission("guilds.general.universe"))
                .executes(this::handleUniverse))
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6╔══════════════════════════════════════════════╗");
        sender.sendMessage("§6║           §e§lGUILDS GENERAL COMMANDS§r§6          ║");
        sender.sendMessage("§6╠══════════════════════════════════════════════╣");
        sender.sendMessage("§6║ §f/guilds version§7                               §6║");
        sender.sendMessage("§6║   §8» Show plugin version                    §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/guilds time§7                                 §6║");
        sender.sendMessage("§6║   §8» Show current server time                §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/guilds top§7                                 §6║");
        sender.sendMessage("§6║   §8» Show top statistics                     §6║");
        sender.sendMessage("§6║                                                §6║");
        sender.sendMessage("§6║ §f/guilds prices§7                              §6║");
        sender.sendMessage("§6║   §8» Show town and plot costs                §6║");
        sender.sendMessage("§6║                                                ║");
        sender.sendMessage("§6║ §f/guilds chat§7                                §6║");
        sender.sendMessage("§6║   §8» Send message to town chat               §6║");
        sender.sendMessage("§6║                                                ║");
        sender.sendMessage("§6║ §f/guilds universe§7                            §6║");
        sender.sendMessage("§6║   §8» Show universe statistics                §6║");
        sender.sendMessage("§6╚══════════════════════════════════════════════╝");
        return Command.SINGLE_SUCCESS;
    }

    private int handleVersion(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6=== Guilds Plugin Information ===");
        sender.sendMessage("§fVersion: §a" + plugin.getDescription().getVersion());
        sender.sendMessage("§fAuthor: §e" + plugin.getDescription().getAuthors());
        sender.sendMessage("§fWebsite: §b" + plugin.getDescription().getWebsite());
        sender.sendMessage("§fDescription: §7" + plugin.getDescription().getDescription());
        return Command.SINGLE_SUCCESS;
    }

    private int handleTime(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        sender.sendMessage("§6=== Time Information ===");
        sender.sendMessage("§fServer Time: §a" + now.format(formatter));
        sender.sendMessage("§fDay: §e" + now.getDayOfWeek());
        sender.sendMessage("§fYear: §e" + now.getYear());
        return Command.SINGLE_SUCCESS;
    }

    private int showTopHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Top Commands ===");
        sender.sendMessage("§f/guilds top residents§7 - Top residents by town count");
        sender.sendMessage("§f/guilds top towns§7 - Top towns by resident count");
        sender.sendMessage("§f/guilds top land§7 - Top towns by land count");
        return Command.SINGLE_SUCCESS;
    }

    private int handleTopResidents(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Top Residents ===");
        sender.sendMessage("§7This command is not yet implemented. Resident rankings will be available in a future update.");
        return Command.SINGLE_SUCCESS;
    }

    private int handleTopTowns(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            sender.sendMessage("§eNo towns found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Towns by Residents ===");

        // Sort towns by resident count
        towns.sort((a, b) -> Integer.compare(b.getResidentCount(), a.getResidentCount()));

        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            var town = towns.get(i);
            int residentCount = townService.getTownResidentCount(town.getName());
            sender.sendMessage("§f" + (i + 1) + ". §a" + town.getName() + " §7- §e" + residentCount + " residents");
        }

        if (towns.size() > 10) {
            sender.sendMessage("§7And " + (towns.size() - 10) + " more towns...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTopLand(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            sender.sendMessage("§eNo towns found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Towns by Land ===");

        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            var town = towns.get(i);
            int landCount = plotService.getTownBlockCount(town.getName());
            sender.sendMessage("§f" + (i + 1) + ". §a" + town.getName() + " §7- §e" + landCount + " chunks");
        }

        if (towns.size() > 10) {
            sender.sendMessage("§7And " + (towns.size() - 10) + " more towns...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handlePrices(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6=== Guilds Prices ===");
        sender.sendMessage("§fTown Creation: §6$1000");
        sender.sendMessage("§fTown Claim: §6$50 per chunk");
        sender.sendMessage("§fPlot Claim: §6$25 per plot");
        sender.sendMessage("§fPlot Purchase: §6Variable (set by owner)");
        sender.sendMessage("§7Note: These are default prices and may be modified by server administrators.");
        return Command.SINGLE_SUCCESS;
    }

    private int handleChatHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Town Chat Commands ===");
        sender.sendMessage("§f/guilds chat <message>§7 - Send message to town chat");
        sender.sendMessage("§f/guilds chat tc <message>§7 - Alias for town chat");
        sender.sendMessage("§7Town chat sends messages to all online residents of your town.");
        return Command.SINGLE_SUCCESS;
    }

    private int handleTownChat(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty() || !resident.get().hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.get().getTown();
        String playerName = player.getName();

        // Format the message
        String formattedMessage = "§2[TC] §f" + playerName + "§7: §f" + message;

        // Broadcast to all online town residents
        int messageCount = 0;
        for (var onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID onlineUuid = onlinePlayer.getUniqueId();
            var onlineResident = residentService.getResident(onlineUuid);

            if (onlineResident.isPresent() && onlineResident.get().hasTown() &&
                onlineResident.get().getTown().equals(townName)) {
                onlinePlayer.sendMessage(formattedMessage);
                messageCount++;
            }
        }

        // Log to console
        plugin.getLogger().info("[TownChat] " + playerName + " -> " + townName + " (" + messageCount + " recipients): " + message);

        if (messageCount == 0) {
            player.sendMessage("§7No other town members are currently online.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleUniverse(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        int totalTowns = townService.getAllTowns().size();
        int totalResidents = residentService.getAllResidents().size();
        int totalPlots = plotService.getAllTownBlocks().size();
        int onlinePlayers = org.bukkit.Bukkit.getOnlinePlayers().size();

        sender.sendMessage("§6=== Universe Statistics ===");
        sender.sendMessage("§fTotal Towns: §a" + totalTowns);
        sender.sendMessage("§fTotal Residents: §a" + totalResidents);
        sender.sendMessage("§fTotal Plots: §a" + totalPlots);
        sender.sendMessage("§fOnline Players: §a" + onlinePlayers);

        if (totalTowns > 0) {
            double avgResidents = (double) totalResidents / totalTowns;
            double avgPlots = (double) totalPlots / totalTowns;
            sender.sendMessage("§fAvg Residents/Town: §e" + String.format("%.1f", avgResidents));
            sender.sendMessage("§fAvg Plots/Town: §e" + String.format("%.1f", avgPlots));
        }

        return Command.SINGLE_SUCCESS;
    }
}