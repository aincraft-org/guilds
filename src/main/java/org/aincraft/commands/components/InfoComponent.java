package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.aincraft.service.SpawnService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.progression.GuildProgression;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.ProgressionService;
import org.aincraft.subregion.RegionTypeLimit;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for viewing guild information.
 */
public class InfoComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;
    private final RelationshipService relationshipService;
    private final SubregionService subregionService;
    private final ProgressionService progressionService;
    private final ProgressionConfig progressionConfig;

    @Inject
    public InfoComponent(GuildLifecycleService lifecycleService, GuildMemberService memberService,
                         TerritoryService territoryService, RelationshipService relationshipService,
                         SubregionService subregionService, ProgressionService progressionService,
                         ProgressionConfig progressionConfig) {
        this.lifecycleService = lifecycleService;
        this.memberService = memberService;
        this.territoryService = territoryService;
        this.relationshipService = relationshipService;
        this.subregionService = subregionService;
        this.progressionService = progressionService;
        this.progressionConfig = progressionConfig;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getPermission() {
        return "guilds.info";
    }

    @Override
    public String getUsage() {
        return "/g info [guild-name]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to view guild info"));
            return true;
        }

        Guild guild = null;

        if (args.length >= 2) {
            // Look up guild by name
            guild = lifecycleService.getGuildByName(args[1]);
        } else {
            // Use player's current guild
            guild = memberService.getPlayerGuild(player.getUniqueId());
        }

        if (guild == null) {
            if (args.length >= 2) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + args[1] + "' not found"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            }
            return true;
        }

        displayGuildInfo(player, guild);
        return true;
    }

    /**
     * Displays formatted guild information.
     *
     * @param player the player to send the info to
     * @param guild the guild to display
     */
    private void displayGuildInfo(Player player, Guild guild) {
        // Header with guild name embedded
        String guildName = guild.getName();
        int totalWidth = 40;
        int nameLength = guildName.length() + 4; // +4 for brackets and spaces
        int sideLength = (totalWidth - nameLength) / 2;

        StringBuilder header = new StringBuilder();
        header.append("<dark_gray>o<gold>0<yellow>o<gold>.");
        for (int i = 0; i < Math.max(0, sideLength - 4); i++) {
            header.append("_");
        }
        header.append("<dark_gray>[ <white>").append(guildName).append(" <dark_gray>]");
        for (int i = 0; i < Math.max(0, sideLength - 4); i++) {
            header.append("<gold>_");
        }
        header.append("<yellow>o<gold>0<dark_gray>o");
        player.sendMessage(MessageFormatter.deserialize(header.toString()));
        player.sendMessage(Component.newline());

        // Description if exists
        if (guild.getDescription() != null && !guild.getDescription().isEmpty()) {
            player.sendMessage(Component.empty());
            player.sendMessage(MessageFormatter.deserialize("  <gray>\"" + guild.getDescription() + "\""));
            player.sendMessage(Component.empty());
        }

        // Owner with hover
        var ownerPlayer = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId());
        String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : "Unknown";
        Component ownerLine = Component.text()
            .append(Component.text("  Owner: ", NamedTextColor.GRAY))
            .append(Component.text(ownerName, NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text()
                    .append(Component.text("Player: ", NamedTextColor.YELLOW))
                    .append(Component.text(ownerName, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("UUID: ", NamedTextColor.YELLOW))
                    .append(Component.text(guild.getOwnerId().toString(), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("Status: ", NamedTextColor.YELLOW))
                    .append(Component.text(ownerPlayer.isOnline() ? "Online" : "Offline",
                        ownerPlayer.isOnline() ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .build())))
            .build();
        player.sendMessage(ownerLine);

        // Created date with hover
        String dateOnly = new SimpleDateFormat("yyyy-MM-dd").format(new Date(guild.getCreatedAt()));
        String fullDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date(guild.getCreatedAt()));
        long daysAgo = (System.currentTimeMillis() - guild.getCreatedAt()) / (1000 * 60 * 60 * 24);
        Component createdLine = Component.text()
            .append(Component.text("  Created: ", NamedTextColor.GRAY))
            .append(Component.text(dateOnly, NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text()
                    .append(Component.text("Created: ", NamedTextColor.YELLOW))
                    .append(Component.text(fullDateTime, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Days ago: ", NamedTextColor.YELLOW))
                    .append(Component.text(String.valueOf(daysAgo), NamedTextColor.GRAY))
                    .build())))
            .build();
        player.sendMessage(createdLine);

        player.sendMessage(Component.empty());

        // Level and progression section
        player.sendMessage(MessageFormatter.deserialize("  <yellow>Progression"));
        displayLevelProgress(player, guild);

        player.sendMessage(Component.empty());

        // Statistics section
        player.sendMessage(MessageFormatter.deserialize("  <yellow>Statistics"));
        player.sendMessage(MessageFormatter.deserialize("  <dark_gray>• <gray>Members: <white>" +
            guild.getMemberCount() + "<dark_gray>/<white>" + guild.getMaxMembers()));

        int claimedChunks = territoryService.getGuildChunks(guild.getId()).size();
        int maxChunks = guild.getMaxChunks();
        player.sendMessage(MessageFormatter.deserialize("  <dark_gray>• <gray>Chunks: <white>" +
            claimedChunks + "<dark_gray>/<white>" + maxChunks));

        // Display toggles inline
        String explosions = guild.isExplosionsAllowed() ? "<green>✓" : "<red>✗";
        String fire = guild.isFireAllowed() ? "<green>✓" : "<red>✗";
        String isPublic = guild.isPublic() ? "<green>Public" : "<red>Private";
        player.sendMessage(MessageFormatter.deserialize("  <dark_gray>• <gray>Explosions: " + explosions +
            "  <dark_gray>• <gray>Fire: " + fire + "  <dark_gray>• <gray>Access: " + isPublic));

        player.sendMessage(Component.empty());

        // Relationships
        displayRelationships(player, guild);

        // Region limits
        displayRegionLimits(player, guild);

    }



    /**
     * Displays guild relationships (allies and enemies).
     *
     * @param player the player to send the info to
     * @param guild the guild to display relationships for
     */
    private void displayRelationships(Player player, Guild guild) {
        List<String> allies = relationshipService.getAllies(guild.getId());
        List<String> enemies = relationshipService.getEnemies(guild.getId());

        // Only show section if there are relationships
        if (!allies.isEmpty() || !enemies.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("  <yellow>Relationships"));

            // Display allies
            if (!allies.isEmpty()) {
                player.sendMessage(MessageFormatter.deserialize("  <dark_gray>• <gray>Allies <green>(" + allies.size() + ")<gray>:"));
                for (String allyGuildId : allies) {
                    Guild allyGuild = lifecycleService.getGuildById(allyGuildId);
                    if (allyGuild != null) {
                        player.sendMessage(MessageFormatter.deserialize("    <green>• " + allyGuild.getName()));
                    }
                }
            }

            // Display enemies
            if (!enemies.isEmpty()) {
                player.sendMessage(MessageFormatter.deserialize("  <dark_gray>• <gray>Enemies <red>(" + enemies.size() + ")<gray>:"));
                for (String enemyGuildId : enemies) {
                    Guild enemyGuild = lifecycleService.getGuildById(enemyGuildId);
                    if (enemyGuild != null) {
                        player.sendMessage(MessageFormatter.deserialize("    <red>• " + enemyGuild.getName()));
                    }
                }
            }

            player.sendMessage(Component.empty());
        }
    }

    /**
     * Displays region type limits and usage for the guild.
     */
    private void displayRegionLimits(Player player, Guild guild) {
        List<RegionTypeLimit> limits = subregionService.getLimitRepository().findAll();
        if (limits.isEmpty()) {
            return;
        }

        player.sendMessage(MessageFormatter.deserialize("  <yellow>Region Limits"));
        for (RegionTypeLimit limit : limits) {
            long usage = subregionService.getTypeUsage(guild.getId(), limit.typeId());
            long max = limit.maxTotalVolume();
            double percent = max > 0 ? (usage * 100.0) / max : 0;

            String displayName = subregionService.getTypeRegistry().getType(limit.typeId())
                    .map(SubregionType::getDisplayName)
                    .orElse(limit.typeId());

            String color = percent >= 90 ? "<red>" : percent >= 70 ? "<yellow>" : "<green>";
            player.sendMessage(MessageFormatter.deserialize(
                    "  <dark_gray>• <gray>" + displayName + ": " + color +
                    formatNumber(usage) + "<dark_gray>/<white>" + formatNumber(max) +
                    " <dark_gray>(" + String.format("%.0f", percent) + "%)"));
        }
        player.sendMessage(Component.empty());
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    /**
     * Displays guild level and XP progress bar.
     *
     * @param player the player to send the info to
     * @param guild the guild to display level progress for
     */
    private void displayLevelProgress(Player player, Guild guild) {
        GuildProgression progression = progressionService.getOrCreateProgression(guild.getId());

        int level = progression.getLevel();
        long currentXp = progression.getCurrentXp();
        long xpRequired = progressionService.calculateXpRequired(level + 1);
        int maxLevel = progressionConfig.getMaxLevel();

        // Build level line
        String levelText = level >= maxLevel
            ? String.format("  <dark_gray>• <gray>Level: <gold>%d <dark_gray>(MAX)", level)
            : String.format("  <dark_gray>• <gray>Level: <gold>%d <dark_gray>/ <gold>%d", level, maxLevel);
        player.sendMessage(MessageFormatter.deserialize(levelText));

        // Build XP progress bar only if not at max level
        if (level < maxLevel) {
            double progress = (double) currentXp / xpRequired;
            int barLength = 24;
            int filledBars = (int) (progress * barLength);

            StringBuilder progressBar = new StringBuilder();
            progressBar.append("  <dark_gray>• <gray>XP: <gray>[");

            // Add filled portion in green
            for (int i = 0; i < filledBars; i++) {
                progressBar.append("<green>|");
            }

            // Add empty portion in dark gray
            for (int i = filledBars; i < barLength; i++) {
                progressBar.append("<dark_gray>|");
            }

            progressBar.append("<gray>] ");
            progressBar.append(String.format("<yellow>%.0f%%", progress * 100));
            progressBar.append(" <dark_gray>(<white>");
            progressBar.append(String.format("%,d", currentXp));
            progressBar.append("<dark_gray>/<white>");
            progressBar.append(String.format("%,d", xpRequired));
            progressBar.append("<dark_gray>)");

            player.sendMessage(MessageFormatter.deserialize(progressBar.toString()));
        }
    }
}
