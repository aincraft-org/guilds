package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.gui.TechTreeGUI;
import org.aincraft.towny.models.TechTreeNode;
import org.aincraft.towny.models.TechTreeBranch;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.*;

import java.util.*;

/**
 * Brigadier command for the tech tree system.
 * /techtree — open GUI
 * /techtree info [node] — show node details
 * /techtree unlock <node> — unlock a node
 * /techtree list [branch] — list nodes by branch
 */
public class TechTreeBrigadierCommand {

    private final TownyPlugin plugin;
    private final TechTreeService techTreeService;
    private final TownService townService;
    private final ResidentService residentService;
    private final TechTreeGUI techTreeGUI;

    @Inject
    public TechTreeBrigadierCommand(TownyPlugin plugin, TechTreeService techTreeService,
                                    TownService townService, ResidentService residentService,
                                    TechTreeGUI techTreeGUI) {
        this.plugin = plugin;
        this.techTreeService = techTreeService;
        this.townService = townService;
        this.residentService = residentService;
        this.techTreeGUI = techTreeGUI;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("techtree")
            .requires(source -> source.getSender().hasPermission("towny.techtree"))
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
                        // Suggest available nodes for the player's town
                        Town town = getPlayerTown(ctx.getSource().getSender());
                        if (town != null) {
                            for (TechTreeNode node : techTreeService.getAvailableNodes(town)) {
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
            .build();
    }

    private int handleOpenGUI(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Town town = getPlayerTown(player);
        if (town == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        techTreeService.loadTownTechData(town);
        techTreeGUI.openTechTree(player, town);
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

        // Show unlock status if player is in a town
        if (sender instanceof org.bukkit.entity.Player player) {
            Town town = getPlayerTown(player);
            if (town != null) {
                boolean unlocked = techTreeService.isTechNodeUnlocked(town, nodeId);
                boolean available = techTreeService.canUnlockNode(town, nodeId);
                sender.sendMessage("");
                if (unlocked) {
                    sender.sendMessage("§a✓ Already unlocked");
                } else if (available) {
                    sender.sendMessage("§e▸ Available to unlock (§d" + town.getTechPoints() + "§e tech points)");
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

        Town town = getPlayerTown(player);
        if (town == null) {
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

        if (techTreeService.isTechNodeUnlocked(town, nodeId)) {
            player.sendMessage("§e" + node.getName() + " is already unlocked!");
            return 0;
        }

        if (!techTreeService.canUnlockNode(town, nodeId)) {
            player.sendMessage("§cCannot unlock " + node.getName() + "!");
            if (town.getTechPoints() < node.getCost()) {
                player.sendMessage("§7  Not enough tech points. Need §d" + node.getCost() + "§7, have §d" + town.getTechPoints());
            }
            if (node.getPrerequisites() != null) {
                for (String prereqId : node.getPrerequisites()) {
                    if (!techTreeService.isTechNodeUnlocked(town, prereqId)) {
                        techTreeService.getNode(prereqId).ifPresent(prereq ->
                            player.sendMessage("§7  Missing prerequisite: §f" + prereq.getName())
                        );
                    }
                }
            }
            return 0;
        }

        boolean success = techTreeService.unlockTechNode(town, nodeId);
        if (success) {
            player.sendMessage("§a✓ Unlocked " + (node.getBranch() != null ? node.getBranch().getColorCode() : "§f") + node.getName() + "§a!");
            player.sendMessage("§7Tech points remaining: §d" + town.getTechPoints());
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

    private Town getPlayerTown(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) return null;
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .flatMap(townName -> townService.getTown(townName))
                .orElse(null);
    }
}
