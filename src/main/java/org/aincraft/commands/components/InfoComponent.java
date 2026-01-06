package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.aincraft.service.SpawnService;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(sender, "<error>You don't have permission to use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(sender, "<error>You don't have permission to use this command</error>");
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
                Mint.sendMessage(player, "<error>Guild not found: <secondary>" + args[1] + "</secondary></error>");
            } else {
                Mint.sendMessage(player, "<error>You are not in a guild</error>");
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
        TextColor guildColor = getGuildColor(guild.getColor());
        int totalWidth = 40;
        int nameLength = guildName.length() + 4; // +4 for brackets and spaces
        int sideLength = (totalWidth - nameLength) / 2;

        // Build header component programmatically to apply guild color
        var headerBuilder = Component.text()
            .append(Component.text("o", NamedTextColor.DARK_GRAY))
            .append(Component.text("0", NamedTextColor.GOLD))
            .append(Component.text("o", NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GOLD));

        for (int i = 0; i < Math.max(0, sideLength - 4); i++) {
            headerBuilder.append(Component.text("_", NamedTextColor.GOLD));
        }

        headerBuilder.append(Component.text("[ ", NamedTextColor.DARK_GRAY))
            .append(Component.text(guildName, guildColor))
            .append(Component.text(" ]", NamedTextColor.DARK_GRAY));

        for (int i = 0; i < Math.max(0, sideLength - 4); i++) {
            headerBuilder.append(Component.text("_", NamedTextColor.GOLD));
        }

        headerBuilder.append(Component.text("o", NamedTextColor.YELLOW))
            .append(Component.text("0", NamedTextColor.GOLD))
            .append(Component.text("o", NamedTextColor.DARK_GRAY));

        player.sendMessage(headerBuilder.build());
        Mint.sendMessage(player, "");

        // Description if exists
        if (guild.getDescription() != null && !guild.getDescription().isEmpty()) {
            Mint.sendMessage(player, "");
            Mint.sendMessage(player, "  <neutral>\"" + guild.getDescription() + "\"</neutral>");
            Mint.sendMessage(player, "");
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

        Mint.sendMessage(player, "");

        // Level and progression section
        Mint.sendMessage(player, "  <secondary>Progression</secondary>");
        displayLevelProgress(player, guild);

        Mint.sendMessage(player, "");

        // Statistics section
        Mint.sendMessage(player, "  <secondary>Statistics</secondary>");
        Mint.sendMessage(player, "  <neutral>• <neutral>Members: <primary>" +
                guild.getMemberCount() + "<neutral>/<primary>" + guild.getMaxMembers() + "</neutral></neutral>");

        int claimedChunks = territoryService.getGuildChunks(guild.getId()).size();
        int maxChunks = guild.getMaxChunks();
        Mint.sendMessage(player, "  <neutral>• <neutral>Chunks: <primary>" +
                claimedChunks + "<neutral>/<primary>" + maxChunks + "</neutral></neutral>");

        // Display toggles inline
        String explosions = guild.isExplosionsAllowed() ? "<success>✓" : "<error>✗";
        String fire = guild.isFireAllowed() ? "<success>✓" : "<error>✗";
        String isPublic = guild.isPublic() ? "<success>Public" : "<error>Private";
        Mint.sendMessage(player, "  <neutral>• <neutral>Explosions: " + explosions +
                "  <neutral>• <neutral>Fire: " + fire + "  <neutral>• <neutral>Access: " + isPublic + "</neutral></neutral></neutral>");

        Mint.sendMessage(player, "");

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
        List<UUID> allies = relationshipService.getAllies(guild.getId());
        List<UUID> enemies = relationshipService.getEnemies(guild.getId());

        // Only show section if there are relationships
        if (!allies.isEmpty() || !enemies.isEmpty()) {
            Mint.sendMessage(player, "  <secondary>Relationships</secondary>");

            // Display allies
            if (!allies.isEmpty()) {
                Mint.sendMessage(player, "  <neutral>• <neutral>Allies <success>(" + allies.size() + ")<neutral>:</neutral></neutral>");
                for (UUID ally : allies) {
                    Guild allyGuild = lifecycleService.getGuildById(ally);
                    if (allyGuild != null) {
                        Mint.sendMessage(player, "    <success>• <secondary>" + allyGuild.getName() + "</secondary></success>");
                    }
                }
            }

            // Display enemies
            if (!enemies.isEmpty()) {
                Mint.sendMessage(player, "  <neutral>• <neutral>Enemies <error>(" + enemies.size() + ")<neutral>:</neutral></neutral>");
                for (UUID enemy : enemies) {
                    Guild enemyGuild = lifecycleService.getGuildById(enemy);
                    if (enemyGuild != null) {
                        Mint.sendMessage(player, "    <error>• <secondary>" + enemyGuild.getName() + "</secondary></error>");
                    }
                }
            }

            Mint.sendMessage(player, "");
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

        Mint.sendMessage(player, "  <secondary>Region Limits</secondary>");
        for (RegionTypeLimit limit : limits) {
            long usage = subregionService.getTypeUsage(guild.getId(), limit.typeId());
            long max = limit.maxTotalVolume();
            double percent = max > 0 ? (usage * 100.0) / max : 0;

            String displayName = subregionService.getTypeRegistry().getType(limit.typeId())
                    .map(SubregionType::getDisplayName)
                    .orElse(limit.typeId());

            String color = percent >= 90 ? "<error>" : percent >= 70 ? "<warning>" : "<success>";
            Mint.sendMessage(player,
                    "  <neutral>• <neutral>" + displayName + ": " + color +
                    formatNumber(usage) + "<neutral>/<primary>" + formatNumber(max) + "</neutral>" +
                    " <neutral>(" + String.format("%.0f", percent) + "%)</neutral></neutral>");
        }
        Mint.sendMessage(player, "");
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
            ? String.format("  <neutral>• <neutral>Level: <secondary>%d <neutral>(MAX)</neutral></neutral>", level)
            : String.format("  <neutral>• <neutral>Level: <secondary>%d <neutral>/ <secondary>%d</neutral></neutral>", level, maxLevel);
        Mint.sendMessage(player, levelText);

        // Build XP progress bar only if not at max level
        if (level < maxLevel) {
            double progress = (double) currentXp / xpRequired;
            int barLength = 24;
            int filledBars = (int) (progress * barLength);

            StringBuilder progressBar = new StringBuilder();
            progressBar.append("  <neutral>• <neutral>XP: <neutral>[</neutral>");

            // Add filled portion in green
            for (int i = 0; i < filledBars; i++) {
                progressBar.append("<success>|");
            }

            // Add empty portion in dark gray
            for (int i = filledBars; i < barLength; i++) {
                progressBar.append("<neutral>|");
            }

            progressBar.append("<neutral>] </neutral>");
            progressBar.append(String.format("<info>%.0f%%</info>", progress * 100));
            progressBar.append(" <neutral>(<primary>");
            progressBar.append(String.format("%,d", currentXp));
            progressBar.append("<neutral>/<primary>");
            progressBar.append(String.format("%,d", xpRequired));
            progressBar.append("<neutral>)</neutral>");

            Mint.sendMessage(player, progressBar.toString());
        }
    }

    /**
     * Converts a guild color (hex or named color) to a TextColor for display.
     * Falls back to GOLD if no color is set or invalid.
     *
     * @param color the color string (hex #RRGGBB or named color like "red", "blue")
     * @return a TextColor to use for display
     */
    private TextColor getGuildColor(String color) {
        if (color == null) {
            return NamedTextColor.GOLD;
        }

        // Check if hex format
        if (color.startsWith("#") && color.length() == 7) {
            try {
                return TextColor.fromHexString(color);
            } catch (IllegalArgumentException e) {
                // Fall through to named color check
            }
        }

        // Check if named color
        TextColor namedColor = NamedTextColor.NAMES.value(color.toLowerCase());
        if (namedColor != null) {
            return namedColor;
        }

        // Default to GOLD if invalid
        return NamedTextColor.GOLD;
    }
}
