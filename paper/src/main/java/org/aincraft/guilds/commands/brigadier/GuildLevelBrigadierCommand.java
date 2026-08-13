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
import org.aincraft.guilds.models.ResourceType;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.ResourceService;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
        return Commands.literal("guildlevel")
            .requires(source -> source.getSender().hasPermission("guilds.level"))
            .executes(this::showHelp)
            .then(Commands.literal("level")
                .executes(this::handleLevel))
            .then(Commands.literal("deposit")
                .then(Commands.argument("resource", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String resource : resourceService.getSupportedResourceTypes()) {
                            String suggestion = resource.toUpperCase(Locale.ROOT);
                            if (suggestion.toLowerCase(Locale.ROOT)
                                    .startsWith(builder.getRemainingLowerCase())) {
                                builder.suggest(suggestion);
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
                            if (type.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
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

        player.sendMessage("§e=== Guild Level Commands ===");

        player.sendMessage("§f/guildlevel level§7 - Show your guild level and progress");
        player.sendMessage("§f/guildlevel deposit <resource> <amount>§7 - Contribute resources to upgrade");
        player.sendMessage("§f/guildlevel bank§7 - View guild resource bank");
        player.sendMessage("§f/guildlevel upgrade§7 - Upgrade guild to next level");
        player.sendMessage("§f/guildlevel contributions§7 - View contribution statistics");
        player.sendMessage("§f/guildlevel top [type] [count]§7 - Show top guilds (level/residents/balance/techpoints)");

        player.sendMessage("§7");
        player.sendMessage("§7Supported Resources: "
                + String.join(", ", resourceService.getSupportedResourceTypes()).toUpperCase(Locale.ROOT));
        player.sendMessage("§7Only configured guild upgrade resources can be deposited.");
        player.sendMessage("§7Example: /guildlevel deposit DIAMOND 10");
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
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cGuild not found!");
            return 0;
        }

        Guild guild = guildOpt.get();

        player.sendMessage("§e=== Guild Level Information ===");

        player.sendMessage("§eGuild: §b" + guild.getName());
        player.sendMessage("§eCurrent Level: §a" + guild.getGuildLevel());
        player.sendMessage("§eTech Points: §d" + guild.getTechPoints());
        player.sendMessage("§eClaim Limit: §a" + guild.getMaxClaimLimit() + " chunks");
        player.sendMessage("§eAssistant Slots: §a" + guild.getMaxAssistantSlots());
        player.sendMessage("§eDaily Income Bonus: §6§" + String.format("%.2f", guild.getDailyIncomeBonus()));

        if (guild.getGuildLevel() < guildLevelService.getMaxLevel()) {
            player.sendMessage("§eNext Level: §a" + (guild.getGuildLevel() + 1));
            player.sendMessage("§7  Progress: §eUse /guild level deposit to contribute resources");
        } else {
            player.sendMessage("§aYour guild is at the maximum level!");
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
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }
        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cGuild not found!");
            return 0;
        }

        Guild guild = guildOpt.get();
        String resourceType = StringArgumentType.getString(ctx, "resource");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ResourceService.ContributionResult result = resourceService.processContribution(
                guild, player.getUniqueId(), resourceType, amount);
        if (!result.isSuccessful()) {
            player.sendMessage("§c" + result.getMessage());
            return 0;
        }

        String normalized = normalizeResourceKey(resourceType);
        int newAmount = guild.getUpgradeProgress().getOrDefault(normalized, 0);
        player.sendMessage("§a" + result.getMessage());
        player.sendMessage("§eTotal contributed: " + newAmount + " " + normalized);
        guildLevelService.getNextGuildLevel(guild).ifPresent(nextLevel -> {
            int required = nextLevel.getResourceCosts().entrySet().stream()
                    .filter(entry -> normalizeResourceKey(entry.getKey()).equals(normalized))
                    .mapToInt(Map.Entry::getValue)
                    .findFirst()
                    .orElse(0);
            if (required > 0) {
                String progress = newAmount >= required ? "§aRequirement met"
                        : "§eProgress: " + newAmount + "/" + required;
                player.sendMessage("§7" + normalized + ": " + progress);
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
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }
        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cGuild not found!");
            return 0;
        }

        player.sendMessage("§e=== Guild Resource Bank ===");
        List<org.aincraft.guilds.models.GuildResource> resources =
                resourceService.getGuildResources(guildOpt.get().getId());
        if (resources.isEmpty()) {
            player.sendMessage("§7No resources contributed yet.");
            player.sendMessage("§eUse /guildlevel deposit <resource> <amount> to contribute!");
            return Command.SINGLE_SUCCESS;
        }
        for (org.aincraft.guilds.models.GuildResource resource : resources) {
            if (resource.getAmount() > 0) {
                player.sendMessage("§7" + resource.getResourceType().getNormalizedName()
                        + ": §a" + resource.getAmount());
            }
        }
        player.sendMessage("§7");
        player.sendMessage("§eUse '/guildlevel level' to see upgrade requirements");
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
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cGuild not found!");
            return 0;
        }

        Guild guild = guildOpt.get();
        boolean authorized = guild.getMayorUuid() != null
                && guild.getMayorUuid().equals(player.getUniqueId());
        if (!authorized && !player.hasPermission("guilds.admin.guild")) {
            player.sendMessage("§cOnly the guild mayor or a guild administrator can upgrade this guild.");
            return 0;
        }

        if (guild.getGuildLevel() >= guildLevelService.getMaxLevel()) {
            player.sendMessage("§aYour guild is already at the maximum level!");
            return Command.SINGLE_SUCCESS;
        }

        GuildLevelService.UpgradeResult result = guildLevelService.performGuildUpgrade(guild);

        if (result.isSuccessful()) {
            player.sendMessage("");
            player.sendMessage("§e=== 🎉 GUILD UPGRADE COMPLETE! 🎉 ===");
            player.sendMessage("§aYour guild has been upgraded to level §a" + result.getNewLevel() + "!");
            player.sendMessage("§eYou earned §d" + result.getTechPointsEarned() + "§e tech points!");

            player.sendMessage("§eNew Benefits:");
            player.sendMessage("§7  Claim Limit: §a" + guild.getMaxClaimLimit() + " chunks");
            player.sendMessage("§7  Assistant Slots: §a" + guild.getMaxAssistantSlots());
            player.sendMessage("§7  Daily Income: §6§" + String.format("%.2f", guild.getDailyIncomeBonus()));
        } else {
            player.sendMessage("§c" + result.getMessage());
            player.sendMessage("§eUse '/guild level' to see requirements");
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
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(playerGuild);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cGuild not found!");
            return 0;
        }
        Guild guild = guildOpt.get();

        player.sendMessage("§e=== Guild Contribution Status ===");

        Map<String, Integer> contributions = guildLevelService.calculateTotalContributions(guild);
        if (contributions.isEmpty()) {
            player.sendMessage("§7No resources contributed yet.");
            player.sendMessage("§eUse /guildlevel deposit <resource> <amount> to contribute!");
            return Command.SINGLE_SUCCESS;
        }

        int totalContributions = contributions.values().stream().mapToInt(Integer::intValue).sum();
        player.sendMessage("§eTotal Contributed Items: §a" + totalContributions);
        player.sendMessage("§eGuild Level: §a" + guild.getGuildLevel());
        player.sendMessage("§eContributions by Type:");
        for (Map.Entry<String, Integer> entry : contributions.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage("§7  " + entry.getKey() + ": §b" + entry.getValue());
            }
        }

        guildLevelService.getNextGuildLevel(guild).ifPresent(nextLevel -> {
            player.sendMessage("§eProgress to Level " + nextLevel.getLevel() + ":");
            for (Map.Entry<String, Integer> requirement : nextLevel.getResourceCosts().entrySet()) {
                String key = normalizeResourceKey(requirement.getKey());
                int contributed = contributions.getOrDefault(key, 0);
                String status = contributed >= requirement.getValue() ? "§a✓" : "§c✗";
                player.sendMessage("§7  " + status + " " + key + ": §e"
                        + contributed + "§7/§e" + requirement.getValue());
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

        player.sendMessage("§e=== Top Guilds by " + criteria.substring(0, 1).toUpperCase(Locale.ROOT) + criteria.substring(1) + " ===");

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

    private static String normalizeResourceKey(String resourceType) {
        return ResourceType.fromString(resourceType)
                .map(ResourceType::getNormalizedName)
                .orElseGet(() -> resourceType == null
                        ? "" : resourceType.trim().toLowerCase(Locale.ROOT));
    }
    private String getPlayerGuild(org.bukkit.entity.Player player) {
        UUID playerUuid = player.getUniqueId();
        return residentService.getResident(playerUuid)
                .filter(resident -> resident.hasGuild())
                .map(org.aincraft.guilds.models.Resident::getGuild)
                .orElse(null);
    }


}