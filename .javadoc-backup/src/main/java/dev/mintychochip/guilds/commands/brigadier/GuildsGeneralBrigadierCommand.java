package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;

import java.util.UUID;

/**
 * Brigadier implementation of the guilds general command
 */
public class GuildsGeneralBrigadierCommand {

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
     * Creates a new guilds general brigadier command instance.
     * @param plugin the plugin
     * @param residentService the resident service
     * @param guildService the guild service
     * @param plotService the plot service
     * @param permissionService the permission service
     */
    public GuildsGeneralBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
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
                .then(Commands.literal("guilds")
                    .executes(this::handleTopGuilds))
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
                        .executes(this::handleGuildChat)))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::handleGuildChat)))
            // Universe subcommand
            .then(Commands.literal("universe")
                .requires(source -> source.getSender().hasPermission("guilds.general.universe"))
                .executes(this::handleUniverse))
            .build();
    }

    /**
     * Performs the show help operation.
     * @param ctx the ctx
     * @return the result
     */
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
        sender.sendMessage("§6║   §8» Show guild and plot costs                §6║");
        sender.sendMessage("§6║                                                ║");
        sender.sendMessage("§6║ §f/guilds chat§7                                §6║");
        sender.sendMessage("§6║   §8» Send message to guild chat               §6║");
        sender.sendMessage("§6║                                                ║");
        sender.sendMessage("§6║ §f/guilds universe§7                            §6║");
        sender.sendMessage("§6║   §8» Show universe statistics                §6║");
        sender.sendMessage("§6╚══════════════════════════════════════════════╝");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the version.
     * @param ctx the ctx
     * @return the result
     */
    private int handleVersion(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6=== Guilds Plugin Information ===");
        sender.sendMessage("§fVersion: §a" + plugin.getDescription().getVersion());
        sender.sendMessage("§fAuthor: §e" + plugin.getDescription().getAuthors());
        sender.sendMessage("§fWebsite: §b" + plugin.getDescription().getWebsite());
        sender.sendMessage("§fDescription: §7" + plugin.getDescription().getDescription());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the time.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Performs the show top help operation.
     * @param ctx the ctx
     * @return the result
     */
    private int showTopHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Top Commands ===");
        sender.sendMessage("§f/guilds top residents§7 - Top residents by guild count");
        sender.sendMessage("§f/guilds top guilds§7 - Top guilds by resident count");
        sender.sendMessage("§f/guilds top land§7 - Top guilds by land count");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the top residents.
     * @param ctx the ctx
     * @return the result
     */
    private int handleTopResidents(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Top Residents ===");
        sender.sendMessage("§7This command is not yet implemented. Resident rankings will be available in a future update.");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the top guilds.
     * @param ctx the ctx
     * @return the result
     */
    private int handleTopGuilds(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var guilds = guildService.getAllGuilds();

        if (guilds.isEmpty()) {
            sender.sendMessage("§eNo guilds found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Guilds by Residents ===");

        // Sort guilds by resident count
        guilds.sort((a, b) -> Integer.compare(b.getResidentCount(), a.getResidentCount()));

        for (int i = 0; i < Math.min(guilds.size(), 10); i++) {
            var guild = guilds.get(i);
            int residentCount = guildService.getGuildResidentCount(guild.getName());
            sender.sendMessage("§f" + (i + 1) + ". §a" + guild.getName() + " §7- §e" + residentCount + " residents");
        }

        if (guilds.size() > 10) {
            sender.sendMessage("§7And " + (guilds.size() - 10) + " more guilds...");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the top land.
     * @param ctx the ctx
     * @return the result
     */
    private int handleTopLand(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var guilds = guildService.getAllGuilds();

        if (guilds.isEmpty()) {
            sender.sendMessage("§eNo guilds found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Guilds by Land ===");

        for (int i = 0; i < Math.min(guilds.size(), 10); i++) {
            var guild = guilds.get(i);
            int landCount = plotService.getGuildBlockCount(guild.getName());
            sender.sendMessage("§f" + (i + 1) + ". §a" + guild.getName() + " §7- §e" + landCount + " chunks");
        }

        if (guilds.size() > 10) {
            sender.sendMessage("§7And " + (guilds.size() - 10) + " more guilds...");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the prices.
     * @param ctx the ctx
     * @return the result
     */
    private int handlePrices(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6=== Guilds Prices ===");
        sender.sendMessage("§fGuild Creation: §6$1000");
        sender.sendMessage("§fGuild Claim: §6$50 per chunk");
        sender.sendMessage("§fPlot Claim: §6$25 per plot");
        sender.sendMessage("§fPlot Purchase: §6Variable (set by owner)");
        sender.sendMessage("§7Note: These are default prices and may be modified by server administrators.");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the chat help.
     * @param ctx the ctx
     * @return the result
     */
    private int handleChatHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Guild Chat Commands ===");
        sender.sendMessage("§f/guilds chat <message>§7 - Send message to guild chat");
        sender.sendMessage("§f/guilds chat tc <message>§7 - Alias for guild chat");
        sender.sendMessage("§7Guild chat sends messages to all online residents of your guild.");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the guild chat.
     * @param ctx the ctx
     * @return the result
     */
    private int handleGuildChat(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty() || !resident.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.get().getGuild();
        String playerName = player.getName();

        // Format the message
        String formattedMessage = "§2[TC] §f" + playerName + "§7: §f" + message;

        // Broadcast to all online guild residents
        int messageCount = 0;
        for (var onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID onlineUuid = onlinePlayer.getUniqueId();
            var onlineResident = residentService.getResident(onlineUuid);

            if (onlineResident.isPresent() && onlineResident.get().hasGuild() &&
                onlineResident.get().getGuild().equals(guildName)) {
                onlinePlayer.sendMessage(formattedMessage);
                messageCount++;
            }
        }

        // Log to console
        plugin.getLogger().info("[GuildChat] " + playerName + " -> " + guildName + " (" + messageCount + " recipients): " + message);

        if (messageCount == 0) {
            player.sendMessage("§7No other guild members are currently online.");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the universe.
     * @param ctx the ctx
     * @return the result
     */
    private int handleUniverse(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        int totalGuilds = guildService.getAllGuilds().size();
        int totalResidents = residentService.getAllResidents().size();
        int totalPlots = plotService.getAllGuildBlocks().size();
        int onlinePlayers = org.bukkit.Bukkit.getOnlinePlayers().size();

        sender.sendMessage("§6=== Universe Statistics ===");
        sender.sendMessage("§fTotal Guilds: §a" + totalGuilds);
        sender.sendMessage("§fTotal Residents: §a" + totalResidents);
        sender.sendMessage("§fTotal Plots: §a" + totalPlots);
        sender.sendMessage("§fOnline Players: §a" + onlinePlayers);

        if (totalGuilds > 0) {
            double avgResidents = (double) totalResidents / totalGuilds;
            double avgPlots = (double) totalPlots / totalGuilds;
            sender.sendMessage("§fAvg Residents/Guild: §e" + String.format("%.1f", avgResidents));
            sender.sendMessage("§fAvg Plots/Guild: §e" + String.format("%.1f", avgPlots));
        }

        return Command.SINGLE_SUCCESS;
    }
}