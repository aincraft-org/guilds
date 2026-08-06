package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.gui.TechTreeGUI;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.web.SessionManager;
import org.aincraft.guilds.web.WebServerConfig;
import org.aincraft.guilds.web.TechTreeSession;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Brigadier command for the tech tree system.
 * /techtree — open GUI
 * /techtree info [node] — show node details
 * /techtree unlock <node> — unlock a node
 * /techtree list [branch] — list nodes by branch
 */
public class TechTreeBrigadierCommand {

    private final JavaPlugin plugin;
    private final TechTreeService techTreeService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final TechTreeGUI techTreeGUI;
    private final SessionManager sessionManager;
    private final WebServerConfig webServerConfig;


    public TechTreeBrigadierCommand(JavaPlugin plugin, TechTreeService techTreeService,
                                    GuildService guildService, ResidentService residentService,
                                    TechTreeGUI techTreeGUI, SessionManager sessionManager,
                                    WebServerConfig webServerConfig) {
        this.plugin = plugin;
        this.techTreeService = techTreeService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.techTreeGUI = techTreeGUI;
        this.sessionManager = sessionManager;
        this.webServerConfig = webServerConfig;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("techtree")
            .requires(source -> source.getSender().hasPermission("guilds.techtree"))
            .executes(this::handleOpenGUI)
            .then(Commands.literal("info")
                .then(Commands.argument("node", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (TechTreeNode node : techTreeService.getAllNodes()) {
                            if (node.getId().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                builder.suggest(node.getId());
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(this::handleInfo)))
            .then(Commands.literal("unlock")
                .then(Commands.argument("node", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        // Suggest available nodes for the player's guild
                        Guild guild = getPlayerGuild(ctx.getSource().getSender());
                        if (guild != null) {
                            for (TechTreeNode node : techTreeService.getAvailableNodes(guild)) {
                                if (node.getId().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                    builder.suggest(node.getId());
                                }
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(this::handleUnlock)))
            .then(Commands.literal("list")
                .executes(this::handleListAll)
                .then(Commands.argument("branch", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (TechTreeBranch branch : TechTreeBranch.values()) {
                            if (branch.name().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                builder.suggest(branch.name());
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(this::handleListBranch)))
            .then(Commands.literal("web")
                .executes(this::handleWeb))
            .build();
    }

    private int handleOpenGUI(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        techTreeService.loadGuildTechData(guild);
        techTreeGUI.openTechTree(player, guild);
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String nodeId = StringArgumentType.getString(ctx, "node");

        Optional<TechTreeNode> nodeOpt = techTreeService.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            sender.sendMessage("§cTech node not found: " + nodeId);
            return 0;
        }

        TechTreeNode node = nodeOpt.get();
        String branchColor = node.getBranch() != null ? node.getBranch().getColorCode() : "§f";

        sender.sendMessage("§e=== Tech Node: " + branchColor + node.getName() + " §r§e===");
        sender.sendMessage("§7ID: §f" + node.getId());
        sender.sendMessage("§7Branch: " + branchColor + (node.getBranch() != null ? node.getBranch().getDisplayName() : "None"));
        sender.sendMessage("§7Description: §f" + node.getDescription());
        sender.sendMessage("§7Cost: §d" + node.getCost() + " §7tech points");

        if (node.getPrerequisites() != null && !node.getPrerequisites().isEmpty()) {
            sender.sendMessage("§7Prerequisites:");
            for (String prereqId : node.getPrerequisites()) {
                techTreeService.getNode(prereqId).ifPresentOrElse(
                    prereq -> sender.sendMessage("  §f• " + prereq.getName() + " §7(" + prereqId + ")"),
                    () -> sender.sendMessage("  §c• " + prereqId + " §7(unknown)")
                );
            }
        }

        if (node.getEffects() != null && !node.getEffects().isEmpty()) {
            sender.sendMessage("§7Effects:");
            for (Map.Entry<String, Object> effect : node.getEffects().entrySet()) {
                sender.sendMessage("  §a• §f" + effect.getKey() + ": " + effect.getValue());
            }
        }

        // Show unlock status if player is in a guild
        if (sender instanceof org.bukkit.entity.Player player) {
            Guild guild = getPlayerGuild(player);
            if (guild != null) {
                boolean unlocked = techTreeService.isTechNodeUnlocked(guild, nodeId);
                boolean available = techTreeService.canUnlockNode(guild, nodeId);
                sender.sendMessage("");
                if (unlocked) {
                    sender.sendMessage("§a✓ Already unlocked");
                } else if (available) {
                    sender.sendMessage("§e▸ Available to unlock (§d" + guild.getTechPoints() + "§e tech points)");
                } else {
                    sender.sendMessage("§c✗ Locked — prerequisites not met or insufficient tech points");
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleUnlock(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String nodeId = StringArgumentType.getString(ctx, "node");
        Optional<TechTreeNode> nodeOpt = techTreeService.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            player.sendMessage("§cTech node not found: " + nodeId);
            return 0;
        }

        TechTreeNode node = nodeOpt.get();

        if (techTreeService.isTechNodeUnlocked(guild, nodeId)) {
            player.sendMessage("§e" + node.getName() + " is already unlocked!");
            return 0;
        }

        if (!techTreeService.canUnlockNode(guild, nodeId)) {
            player.sendMessage("§cCannot unlock " + node.getName() + "!");
            if (guild.getTechPoints() < node.getCost()) {
                player.sendMessage("§7  Not enough tech points. Need §d" + node.getCost() + "§7, have §d" + guild.getTechPoints());
            }
            if (node.getPrerequisites() != null) {
                for (String prereqId : node.getPrerequisites()) {
                    if (!techTreeService.isTechNodeUnlocked(guild, prereqId)) {
                        techTreeService.getNode(prereqId).ifPresent(prereq ->
                            player.sendMessage("§7  Missing prerequisite: §f" + prereq.getName())
                        );
                    }
                }
            }
            return 0;
        }

        boolean success = techTreeService.unlockTechNode(guild, nodeId);
        if (success) {
            player.sendMessage("§a✓ Unlocked " + (node.getBranch() != null ? node.getBranch().getColorCode() : "§f") + node.getName() + "§a!");
            player.sendMessage("§7Tech points remaining: §d" + guild.getTechPoints());
        } else {
            player.sendMessage("§cFailed to unlock " + node.getName() + ". Try again.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleListAll(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        List<TechTreeNode> allNodes = techTreeService.getAllNodes();

        if (allNodes.isEmpty()) {
            sender.sendMessage("§7No tech tree nodes configured.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Tech Tree Nodes (" + allNodes.size() + ") ===");

        // Group by branch
        Map<TechTreeBranch, List<TechTreeNode>> byBranch = new LinkedHashMap<>();
        for (TechTreeNode node : allNodes) {
            TechTreeBranch branch = node.getBranch();
            if (branch == null) branch = TechTreeBranch.INFRASTRUCTURE;
            byBranch.computeIfAbsent(branch, k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<TechTreeBranch, List<TechTreeNode>> entry : byBranch.entrySet()) {
            sender.sendMessage("");
            sender.sendMessage(entry.getKey().getColoredName() + " §7(" + entry.getValue().size() + " nodes)");
            for (TechTreeNode node : entry.getValue()) {
                sender.sendMessage("  §f" + node.getName() + " §7[" + node.getId() + "] §7- §d" + node.getCost() + " tp");
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleListBranch(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String branchName = StringArgumentType.getString(ctx, "branch");
        TechTreeBranch branch = TechTreeBranch.fromString(branchName);

        if (branch == null) {
            sender.sendMessage("§cUnknown branch: " + branchName);
            sender.sendMessage("§7Valid branches: INFRASTRUCTURE, DEFENSE, COMMERCE, CULTURE");
            return 0;
        }

        List<TechTreeNode> nodes = techTreeService.getNodesByBranch(branch);
        sender.sendMessage("§e=== " + branch.getColoredName() + " §e(" + nodes.size() + " nodes) ===");

        for (TechTreeNode node : nodes) {
            sender.sendMessage("  §f" + node.getName() + " §7[" + node.getId() + "]");
            sender.sendMessage("    §7" + node.getDescription());
            sender.sendMessage("    §7Cost: §d" + node.getCost() + " tp");

            if (node.getPrerequisites() != null && !node.getPrerequisites().isEmpty()) {
                sender.sendMessage("    §7Requires: §f" + String.join(", ", node.getPrerequisites()));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private Guild getPlayerGuild(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return null;
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(org.aincraft.guilds.models.Resident::getGuild)
                .flatMap(guildName -> guildService.getGuild(guildName))
                .orElse(null);
    }

    private int handleWeb(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("This command can only be used by players.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return 0;
        }

        Guild guild = getPlayerGuild(sender);
        if (guild == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("You must be in a town to use the web tech tree.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return 0;
        }

        TechTreeSession session = sessionManager.createSession(player, guild);
        String host = plugin.getServer().getIp().isEmpty() ? "localhost" : plugin.getServer().getIp();
        String port = String.valueOf(webServerConfig.getPort());
        String url = "https://guilds-techtree.vercel.app/s/" + session.getSessionId() + "?host=" + host + ":" + port;

        player.sendMessage(net.kyori.adventure.text.Component.text("🌿 Tech Tree Web Interface", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        player.sendMessage(net.kyori.adventure.text.Component.text("Click to open: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("[Open Tech Tree]", net.kyori.adventure.text.format.NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(net.kyori.adventure.text.Component.text("Opens in your browser", net.kyori.adventure.text.format.NamedTextColor.GRAY)))));
        player.sendMessage(net.kyori.adventure.text.Component.text("Session expires in " + webServerConfig.getSessionTimeoutMinutes() + " minutes.",
                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));

        return Command.SINGLE_SUCCESS;
    }
}
