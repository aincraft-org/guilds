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
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.ResourceService;
import org.aincraft.towny.services.TownLevelService;
import org.aincraft.towny.services.TownService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier implementation of the town level command
 */
public class TownLevelBrigadierCommand {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final TownLevelService townLevelService;
    private final ResourceService resourceService;

    @Inject
    public TownLevelBrigadierCommand(TownyPlugin plugin, ResidentService residentService,
                                   TownService townService, PlotService plotService,
                                   PermissionService permissionService,
                                   TownLevelService townLevelService,
                                   ResourceService resourceService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.townLevelService = townLevelService;
        this.resourceService = resourceService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("townlevel")
            .requires(source -> source.getSender().hasPermission("towny.level"))
            .executes(this::showHelp)
            .then(Commands.literal("level")
                .executes(this::handleLevel))
            .then(Commands.literal("deposit")
                .then(Commands.argument("resource", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        // Suggest common materials
                        for (String resource : Arrays.asList("DIAMOND", "GOLD_INGOT", "IRON_INGOT", "EMERALD",
                                                             "NETHERITE_INGOT", "COAL", "QUARTZ", "REDSTONE")) {
                            if (resource.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                builder.suggest(resource);
                            }
                        }
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(this::handleDeposit))))
            .then(Commands.literal("bank")
                .executes(this::handleBank))
            .then(Commands.literal("upgrade")
                .executes(this::handleUpgrade))
            .then(Commands.literal("contributions")
                .executes(this::handleContributions))
            .then(Commands.literal("top")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String type : Arrays.asList("level", "residents", "balance", "techpoints")) {
                            if (type.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                builder.suggest(type);
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> handleTop(ctx, 10))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> handleTop(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        player.sendMessage("§e=== Town Level Commands ===");

        player.sendMessage("§f/townlevel level§7 - Show your town level and progress");
        player.sendMessage("§f/townlevel deposit <resource> <amount>§7 - Contribute resources to upgrade");
        player.sendMessage("§f/townlevel bank§7 - View town resource bank");
        player.sendMessage("§f/townlevel upgrade§7 - Upgrade town to next level");
        player.sendMessage("§f/townlevel contributions§7 - View contribution statistics");
        player.sendMessage("§f/townlevel top [type] [count]§7 - Show top towns (level/residents/balance/techpoints)");

        player.sendMessage("§7");
        player.sendMessage("§7Supported Resources: Any Minecraft item (DIAMOND, GOLD_INGOT, NETHERITE_INGOT, etc.)");
        player.sendMessage("§7Example: /townlevel deposit DIAMOND 10");
        player.sendMessage("§7Aliases: /tl deposit DIAMOND 10");

        return Command.SINGLE_SUCCESS;
    }

    private int handleLevel(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Town town = townOpt.get();

        player.sendMessage("§e=== Town Level Information ===");

        player.sendMessage("§eTown: §b" + town.getName());
        player.sendMessage("§eCurrent Level: §a" + town.getTownLevel());
        player.sendMessage("§eTech Points: §d" + town.getTechPoints());
        player.sendMessage("§eClaim Limit: §a" + town.getMaxClaimLimit() + " chunks");
        player.sendMessage("§eAssistant Slots: §a" + town.getMaxAssistantSlots());
        player.sendMessage("§eDaily Income Bonus: §6§" + String.format("%.2f", town.getDailyIncomeBonus()));

        if (town.getTownLevel() < townLevelService.getMaxLevel()) {
            player.sendMessage("§eNext Level: §a" + (town.getTownLevel() + 1));
            player.sendMessage("§7  Progress: §eUse /town level deposit to contribute resources");
        } else {
            player.sendMessage("§aYour town is at the maximum level!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleDeposit(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Town town = townOpt.get();
        String resourceType = StringArgumentType.getString(ctx, "resource").toUpperCase();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        if (!resourceService.isSupportedResourceType(resourceType)) {
            player.sendMessage("§cUnsupported resource type: " + resourceType);
            player.sendMessage("§eSupported resources: Any Minecraft item (DIAMOND, GOLD_INGOT, NETHERITE_INGOT, etc.)");
            return 0;
        }

        // Check if player has enough resources
        if (!hasEnoughResources(player, resourceType, amount)) {
            player.sendMessage("§cYou don't have enough " + resourceType + " in your inventory!");
            player.sendMessage("§eRequired: " + amount + ", Available: " + getResourceCount(player, resourceType));
            return 0;
        }

        // Remove resources from player inventory
        if (!removeResources(player, resourceType, amount)) {
            player.sendMessage("§cFailed to remove resources from your inventory!");
            return 0;
        }

        // Add to town upgrade progress
        int previousAmount = town.getUpgradeProgress().getOrDefault(resourceType, 0);
        town.contributeToUpgrade(resourceType, amount);
        townService.updateTown(town);

        int newAmount = town.getUpgradeProgress().getOrDefault(resourceType, 0);
        player.sendMessage("§aSuccessfully contributed " + amount + " " + resourceType + " to town upgrade!");
        player.sendMessage("§eTotal contributed: " + newAmount + " " + resourceType);

        // Show upgrade progress if applicable
        townLevelService.getNextTownLevel(town).ifPresent(nextLevel -> {
            Map<String, Integer> requirements = nextLevel.getResourceCosts();
            if (requirements.containsKey(resourceType)) {
                int required = requirements.get(resourceType);
                if (newAmount >= required) {
                    player.sendMessage("§aYou have enough " + resourceType + " for the next level!");
                } else {
                    player.sendMessage("§eProgress for next level: " + newAmount + "/" + required + " " + resourceType);
                }
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleBank(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Town town = townOpt.get();

        player.sendMessage("§e=== Town Resource Bank ===");

        Map<String, Integer> progress = town.getUpgradeProgress();
        if (progress.isEmpty()) {
            player.sendMessage("§7No resources contributed yet.");
            player.sendMessage("§eUse /town level deposit <resource> <amount> to contribute!");
            return Command.SINGLE_SUCCESS;
        }

        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage("§7" + entry.getKey().substring(0, 1).toUpperCase() +
                                 entry.getKey().substring(1) + ": " +
                                 "§a" + entry.getValue());
            }
        }

        player.sendMessage("§7");
        player.sendMessage("§eUse '/town level' to see upgrade requirements");

        return Command.SINGLE_SUCCESS;
    }

    private int handleUpgrade(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Town town = townOpt.get();

        if (town.getTownLevel() >= townLevelService.getMaxLevel()) {
            player.sendMessage("§aYour town is already at the maximum level!");
            return Command.SINGLE_SUCCESS;
        }

        TownLevelService.UpgradeResult result = townLevelService.performTownUpgrade(town);

        if (result.isSuccessful()) {
            player.sendMessage("");
            player.sendMessage("§e=== 🎉 TOWN UPGRADE COMPLETE! 🎉 ===");
            player.sendMessage("§aYour town has been upgraded to level §a" + result.getNewLevel() + "!");
            player.sendMessage("§eYou earned §d" + result.getTechPointsEarned() + "§e tech points!");

            player.sendMessage("§eNew Benefits:");
            player.sendMessage("§7  Claim Limit: §a" + town.getMaxClaimLimit() + " chunks");
            player.sendMessage("§7  Assistant Slots: §a" + town.getMaxAssistantSlots());
            player.sendMessage("§7  Daily Income: §6§" + String.format("%.2f", town.getDailyIncomeBonus()));
        } else {
            player.sendMessage("§c" + result.getMessage());
            player.sendMessage("§eUse '/town level' to see requirements");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleContributions(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Town town = townOpt.get();

        player.sendMessage("§e=== Town Contribution Status ===");

        Map<String, Integer> contributions = town.getUpgradeProgress();
        if (contributions.isEmpty()) {
            player.sendMessage("§7No resources contributed yet.");
            player.sendMessage("§eUse /town level deposit <resource> <amount> to contribute!");
            return Command.SINGLE_SUCCESS;
        }

        int totalContributions = contributions.values().stream().mapToInt(Integer::intValue).sum();

        player.sendMessage("§eTotal Contributed Items: §a" + totalContributions);
        player.sendMessage("§eTown Level: §a" + town.getTownLevel());

        player.sendMessage("§eContributions by Type:");
        for (Map.Entry<String, Integer> entry : contributions.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage("§7  " + entry.getKey() + ": §b" + entry.getValue());
            }
        }

        // Show player's progress toward next level
        townLevelService.getNextTownLevel(town).ifPresent(nextLevel -> {
            Map<String, Integer> required = nextLevel.getResourceCosts();
            player.sendMessage("§eProgress to Level " + nextLevel.getLevel() + ":");
            for (Map.Entry<String, Integer> req : required.entrySet()) {
                int has = contributions.getOrDefault(req.getKey(), 0);
                String status = has >= req.getValue() ? "§a✓" : "§c✗";
                player.sendMessage("§7  " + status + " " + req.getKey() + ": " +
                                 "§e" + has + "§7/§e" + req.getValue());
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int handleTop(CommandContext<CommandSourceStack> ctx, int limit) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String criteria = StringArgumentType.getString(ctx, "type");

        List<Town> topTowns = townService.getRankedTowns(criteria, limit);

        player.sendMessage("§e=== Top Towns by " + criteria.substring(0, 1).toUpperCase() + criteria.substring(1) + " ===");

        for (int i = 0; i < topTowns.size(); i++) {
            Town town = topTowns.get(i);
            String value = switch (criteria) {
                case "level" -> String.valueOf(town.getTownLevel());
                case "residents" -> String.valueOf(town.getResidentCount());
                case "balance" -> String.format("%.2f", town.getBalance());
                case "techpoints" -> String.valueOf(town.getTechPoints());
                default -> String.valueOf(town.getTownLevel());
            };

            player.sendMessage("§f" + String.valueOf(i + 1) + ". §b" + town.getName() +
                             "§7 - §e" + value);
        }

        return Command.SINGLE_SUCCESS;
    }

    private String getPlayerTown(org.bukkit.entity.Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);
    }

    private org.bukkit.Material getMaterialForResource(String resourceType) {
        try {
            return org.bukkit.Material.valueOf(resourceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean hasEnoughResources(org.bukkit.entity.Player player, String resourceType, int amount) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) return false;

        int playerAmount = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                playerAmount += item.getAmount();
            }
        }

        return playerAmount >= amount;
    }

    private int getResourceCount(org.bukkit.entity.Player player, String resourceType) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) return 0;

        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }

        return count;
    }

    private boolean removeResources(org.bukkit.entity.Player player, String resourceType, int amount) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) return false;

        int remaining = amount;
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int stackAmount = item.getAmount();
                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
            }
        }

        player.getInventory().setContents(contents);
        player.updateInventory();

        return remaining == 0;
    }

}