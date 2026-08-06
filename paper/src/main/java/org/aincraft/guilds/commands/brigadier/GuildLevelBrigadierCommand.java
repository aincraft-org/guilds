package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.ResourceService;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier implementation of the guild level command
 */
public class GuildLevelBrigadierCommand {

    private final JavaPlugin plugin;
    private final ResidentService residentService;
    private final GuildService guildService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final GuildLevelService guildLevelService;
    private final ResourceService resourceService;


    public GuildLevelBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                                   GuildService guildService, PlotService plotService,
                                   PermissionService permissionService,
                                   GuildLevelService guildLevelService,
                                   ResourceService resourceService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.guildService = guildService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.guildLevelService = guildLevelService;
        this.resourceService = resourceService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("townlevel")
            .requires(source -> source.getSender().hasPermission("guilds.level"))
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
        player.sendMessage("§f/townlevel top [type] [count]§7 - Show top guilds (level/residents/balance/techpoints)");

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

        String playerGuild = getPlayerGuild(player);
        if (playerGuild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Guild guild = guildOpt.get();

        player.sendMessage("§e=== Town Level Information ===");

        player.sendMessage("§eTown: §b" + guild.getName());
        player.sendMessage("§eCurrent Level: §a" + guild.getGuildLevel());
        player.sendMessage("§eTech Points: §d" + guild.getTechPoints());
        player.sendMessage("§eClaim Limit: §a" + guild.getMaxClaimLimit() + " chunks");
        player.sendMessage("§eAssistant Slots: §a" + guild.getMaxAssistantSlots());
        player.sendMessage("§eDaily Income Bonus: §6§" + String.format("%.2f", guild.getDailyIncomeBonus()));

        if (guild.getGuildLevel() < guildLevelService.getMaxLevel()) {
            player.sendMessage("§eNext Level: §a" + (guild.getGuildLevel() + 1));
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

        String playerGuild = getPlayerGuild(player);
        if (playerGuild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Guild guild = guildOpt.get();
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

        // Add to guild upgrade progress
        int previousAmount = guild.getUpgradeProgress().getOrDefault(resourceType, 0);
        guild.contributeToUpgrade(resourceType, amount);
        guildService.updateGuild(guild);

        int newAmount = guild.getUpgradeProgress().getOrDefault(resourceType, 0);
        player.sendMessage("§aSuccessfully contributed " + amount + " " + resourceType + " to town upgrade!");
        player.sendMessage("§eTotal contributed: " + newAmount + " " + resourceType);

        // Show upgrade progress if applicable
        guildLevelService.getNextGuildLevel(guild).ifPresent(nextLevel -> {
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

        String playerGuild = getPlayerGuild(player);
        if (playerGuild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Guild guild = guildOpt.get();

        player.sendMessage("§e=== Town Resource Bank ===");

        Map<String, Integer> progress = guild.getUpgradeProgress();
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

        String playerGuild = getPlayerGuild(player);
        if (playerGuild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Guild guild = guildOpt.get();

        if (guild.getGuildLevel() >= guildLevelService.getMaxLevel()) {
            player.sendMessage("§aYour town is already at the maximum level!");
            return Command.SINGLE_SUCCESS;
        }

        GuildLevelService.UpgradeResult result = guildLevelService.performGuildUpgrade(guild);

        if (result.isSuccessful()) {
            player.sendMessage("");
            player.sendMessage("§e=== 🎉 TOWN UPGRADE COMPLETE! 🎉 ===");
            player.sendMessage("§aYour town has been upgraded to level §a" + result.getNewLevel() + "!");
            player.sendMessage("§eYou earned §d" + result.getTechPointsEarned() + "§e tech points!");

            player.sendMessage("§eNew Benefits:");
            player.sendMessage("§7  Claim Limit: §a" + guild.getMaxClaimLimit() + " chunks");
            player.sendMessage("§7  Assistant Slots: §a" + guild.getMaxAssistantSlots());
            player.sendMessage("§7  Daily Income: §6§" + String.format("%.2f", guild.getDailyIncomeBonus()));
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

        String playerGuild = getPlayerGuild(player);
        if (playerGuild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cTown not found!");
            return 0;
        }

        Guild guild = guildOpt.get();

        player.sendMessage("§e=== Town Contribution Status ===");

        Map<String, Integer> contributions = guild.getUpgradeProgress();
        if (contributions.isEmpty()) {
            player.sendMessage("§7No resources contributed yet.");
            player.sendMessage("§eUse /town level deposit <resource> <amount> to contribute!");
            return Command.SINGLE_SUCCESS;
        }

        int totalContributions = contributions.values().stream().mapToInt(Integer::intValue).sum();

        player.sendMessage("§eTotal Contributed Items: §a" + totalContributions);
        player.sendMessage("§eTown Level: §a" + guild.getGuildLevel());

        player.sendMessage("§eContributions by Type:");
        for (Map.Entry<String, Integer> entry : contributions.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage("§7  " + entry.getKey() + ": §b" + entry.getValue());
            }
        }

        // Show player's progress toward next level
        guildLevelService.getNextGuildLevel(guild).ifPresent(nextLevel -> {
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

        List<Guild> topGuilds = guildService.getRankedGuilds(criteria, limit);

        player.sendMessage("§e=== Top Towns by " + criteria.substring(0, 1).toUpperCase() + criteria.substring(1) + " ===");

        for (int i = 0; i < topGuilds.size(); i++) {
            Guild guild = topGuilds.get(i);
            String value = switch (criteria) {
                case "level" -> String.valueOf(guild.getGuildLevel());
                case "residents" -> String.valueOf(guild.getResidentCount());
                case "balance" -> String.format("%.2f", guild.getBalance());
                case "techpoints" -> String.valueOf(guild.getTechPoints());
                default -> String.valueOf(guild.getGuildLevel());
            };

            player.sendMessage("§f" + String.valueOf(i + 1) + ". §b" + guild.getName() +
                             "§7 - §e" + value);
        }

        return Command.SINGLE_SUCCESS;
    }

    private String getPlayerGuild(org.bukkit.entity.Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasGuild())
                .map(org.aincraft.guilds.models.Resident::getGuild)
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