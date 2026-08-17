package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.guilds.gui.TechTreeGUI;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.GuildService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier command for guild projects (the tech-tree node catalog).
 * /techtree — open GUI
 * /techtree info [node] — show project details
 * /techtree start <node> — start the guild's one active project
 * /techtree unlock <node> — alias for start
 * /techtree clear — clear the active project so another can start
 * /techtree list [branch] — list projects by branch
 */
public class TechTreeBrigadierCommand {

    private final TechTreeService techTreeService;
    private final GuildProjectService guildProjectService;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final TechTreeGUI techTreeGUI;


    public TechTreeBrigadierCommand(TechTreeService techTreeService,
                                    GuildProjectService guildProjectService,
                                    GuildService guildService, ResidentService residentService,
                                    TechTreeGUI techTreeGUI) {
        this.techTreeService = techTreeService;
        this.guildProjectService = guildProjectService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.techTreeGUI = techTreeGUI;
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
            .then(Commands.literal("start")
                .then(Commands.argument("node", StringArgumentType.word())
                    .suggests(this::suggestStartableNodes)
                    .executes(this::handleStart)))
            .then(Commands.literal("unlock")
                .then(Commands.argument("node", StringArgumentType.word())
                    .suggests(this::suggestStartableNodes)
                    .executes(this::handleStart)))
            .then(Commands.literal("clear")
                .executes(this::handleClear))
            .then(Commands.literal("complete")
                .executes(this::handleClear))
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
            player.sendMessage("§cYou are not in a guild!");
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
        sender.sendMessage("§7Cost: §d" + node.getCost() + " §7project skill points");

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
                sender.sendMessage("");
                if (unlocked) {
                    sender.sendMessage("§a✓ Already completed");
                } else if (nodeId.equals(guild.getActiveProjectId())) {
                    sender.sendMessage("§e▸ Active project");
                } else {
                    sender.sendMessage("§7Unspent skill points: §d" + guild.getTechPoints()
                            + "§7 / total §d" + guild.getGuildLevel());
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestStartableNodes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (TechTreeNode node : techTreeService.getAllNodes()) {
            if (node.getId().toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(node.getId());
            }
        }
        return builder.buildFuture();
    }

    private int handleStart(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String nodeId = StringArgumentType.getString(ctx, "node");
        Optional<TechTreeNode> nodeOpt = guildProjectService.getProject(nodeId);
        if (nodeOpt.isEmpty()) {
            player.sendMessage("§cProject not found: " + nodeId);
            return 0;
        }

        TechTreeNode node = nodeOpt.get();
        GuildProjectService.ProjectStartResult result = guildProjectService.startProject(guild, nodeId);
        if (result.isSuccessful()) {
            player.sendMessage("§aStarted project " + (node.getBranch() != null ? node.getBranch().getColorCode() : "§f")
                    + node.getName() + "§a.");
            player.sendMessage("§7Skill points remaining: §d" + result.getUnspentPoints()
                    + "§7 / total §d" + guild.getGuildLevel());
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage("§cCannot start " + node.getName() + ".");
        switch (result.getStatus()) {
            case ALREADY_ACTIVE -> player.sendMessage(
                    "§7A project is already active. Use /techtree clear first.");
            case INSUFFICIENT_POINTS -> player.sendMessage(
                    "§7Need §d" + node.getCost() + "§7 skill points, have §d" + guild.getTechPoints());
            case UNMET_REQUIREMENTS -> player.sendMessage("§7Project requirements are not met.");
            case ALREADY_UNLOCKED -> player.sendMessage("§7That project is already completed.");
            default -> player.sendMessage("§7Unknown project or guild.");
        }
        return 0;
    }

    private int handleClear(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }
        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }
        if (guildProjectService.clearActiveProject(guild)) {
            player.sendMessage("§aCleared the active guild project. You can start another.");
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage("§cNo active guild project to clear.");
        return 0;
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
                sender.sendMessage("  §f" + node.getName() + " §7[" + node.getId() + "] §7- §d" + node.getCost() + " sp");
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
            sender.sendMessage("    §7Cost: §d" + node.getCost() + " skill points");

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

}
