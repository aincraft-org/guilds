package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import dev.mintychochip.guilds.gui.TechTreeGUI;
import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.models.TechTreeBranch;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.TechTreeService;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.guilds.services.GuildService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier command for guild projects (the tech-tree node catalog).
 * /g upgrade — open GUI
 * /g upgrade info [node] — show project details
 * /g upgrade start <node> — start the guild's one active project
 * /g upgrade unlock <node> — alias for start
 * /g upgrade clear — clear the active project so another can start
 * /g upgrade list [branch] — list projects by branch
 */
public class TechTreeBrigadierCommand {

    /** The tech tree service. */
    private final TechTreeService techTreeService;
    /** The guild project service. */
    private final GuildProjectService guildProjectService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;
    /** The tech tree gui. */
    private final TechTreeGUI techTreeGUI;


    /**
     * Creates a new tech tree brigadier command instance.
     * @param techTreeService the tech tree service
     * @param guildProjectService the guild project service
     * @param guildService the guild service
     * @param residentService the resident service
     * @param techTreeGUI the tech tree gui
     */
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

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("upgrade")
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
                .executes(this::handleComplete))
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

    /**
     * Legacy {@code /techtree} pointer at {@code /g upgrade}.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildLegacyHint() {
        return Commands.literal("techtree")
                .executes(this::hintMoved)
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(this::hintMoved))
                .build();
    }

    private int hintMoved(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage("§eGuild projects moved. Use /g upgrade.");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the open gui.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Handles the info.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Performs the suggest startable nodes operation.
     * @param ctx the ctx
     * @param builder the builder
     * @return the result
     */
    private CompletableFuture<Suggestions> suggestStartableNodes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (TechTreeNode node : techTreeService.getAllNodes()) {
            if (node.getId().toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(node.getId());
            }
        }
        return builder.buildFuture();
    }

    /**
     * Handles the start.
     * @param ctx the ctx
     * @return the result
     */
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
                    "§7A project is already active. Use /g upgrade clear first.");
            case INSUFFICIENT_POINTS -> player.sendMessage(
                    "§7Need §d" + node.getCost() + "§7 skill points, have §d" + guild.getTechPoints());
            case UNMET_REQUIREMENTS -> player.sendMessage("§7Project requirements are not met.");
            case ALREADY_UNLOCKED -> player.sendMessage("§7That project is already completed.");
            default -> player.sendMessage("§7Unknown project or guild.");
        }
        return 0;
    }

    /**
     * Handles the complete.
     * @param ctx the ctx
     * @return the result
     */
    private int handleComplete(CommandContext<CommandSourceStack> ctx) {
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
        if (guildProjectService.completeActiveProject(guild)) {
            player.sendMessage("§aCompleted the active guild project. You can start another.");
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage("§cNo active guild project to complete.");
        return 0;
    }

    /**
     * Handles the clear.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Handles the list all.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Handles the list branch.
     * @param ctx the ctx
     * @return the result
     */
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

    /**
     * Returns the player guild.
     * @param sender the sender
     * @return the result
     */
    private Guild getPlayerGuild(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return null;
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(dev.mintychochip.guilds.models.Resident::getGuild)
                .flatMap(guildName -> guildService.getGuild(guildName))
                .orElse(null);
    }

}
