package org.aincraft.commands.components;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.RelationshipService;
import org.aincraft.RelationType;
import org.aincraft.RelationStatus;
import org.aincraft.GuildRelationship;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.RegionTypeLimit;
import org.aincraft.subregion.RegionTypeLimitRepository;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Component for viewing guild information.
 */
public class InfoComponent implements GuildCommand {
    private final GuildService guildService;
    private final RelationshipService relationshipService;
    private final SubregionService subregionService;

    public InfoComponent(GuildService guildService, RelationshipService relationshipService,
                         SubregionService subregionService) {
        this.guildService = guildService;
        this.relationshipService = relationshipService;
        this.subregionService = subregionService;
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
            guild = guildService.getGuildByName(args[1]);
        } else {
            // Use player's current guild
            guild = guildService.getPlayerGuild(player.getUniqueId());
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
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Information", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Name", guild.getName()));

        if (guild.getDescription() != null && !guild.getDescription().isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Description", guild.getDescription()));
        }

        // Owner with hover - display actual player name instead of "you"
        var ownerPlayer = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId());
        String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : "Unknown";

        Component ownerLine = Component.text()
            .append(Component.text("Owner", NamedTextColor.YELLOW))
            .append(Component.text(": ", NamedTextColor.WHITE))
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

        player.sendMessage(MessageFormatter.deserialize("<yellow>Members<reset>: <white>" +
            guild.getMemberCount() + "<gray>/<white>" + guild.getMaxMembers()));

        // Created date with hover
        String dateOnly = new SimpleDateFormat("yyyy-MM-dd").format(new Date(guild.getCreatedAt()));
        String fullDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date(guild.getCreatedAt()));
        long daysAgo = (System.currentTimeMillis() - guild.getCreatedAt()) / (1000 * 60 * 60 * 24);

        Component createdLine = Component.text()
            .append(Component.text("Created", NamedTextColor.YELLOW))
            .append(Component.text(": ", NamedTextColor.WHITE))
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

        // Display toggles
        displayToggles(player, guild);

        // Display relationships
        displayRelationships(player, guild);

        // Display region type limits usage
        displayRegionLimits(player, guild);
    }


    /**
     * Displays guild toggle settings and chunk information.
     *
     * @param player the player to send the info to
     * @param guild the guild to display toggles for
     */
    private void displayToggles(Player player, Guild guild) {
        String explosions = guild.isExplosionsAllowed() ? "<green>Enabled</green>" : "<red>Disabled</red>";
        String fire = guild.isFireAllowed() ? "<green>Enabled</green>" : "<red>Disabled</red>";
        String isPublic = guild.isPublic() ? "<green>Public</green>" : "<red>Private</red>";

        player.sendMessage(MessageFormatter.deserialize("<yellow>Explosions<reset>: " + explosions));
        player.sendMessage(MessageFormatter.deserialize("<yellow>Fire Spread<reset>: " + fire));
        player.sendMessage(MessageFormatter.deserialize("<yellow>Access<reset>: " + isPublic));

        int claimedChunks = guildService.getGuildChunkCount(guild.getId());
        int maxChunks = guild.getMaxChunks();
        player.sendMessage(MessageFormatter.deserialize("<yellow>Chunks<reset>: <white>" +
            claimedChunks + "<gray>/<white>" + maxChunks));
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

        // Display allies
        if (!allies.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Allies<reset>: <green>" + allies.size()));
            for (String allyGuildId : allies) {
                Guild allyGuild = guildService.getGuildById(allyGuildId);
                if (allyGuild != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <gray>• <green>" + allyGuild.getName()));
                }
            }
        } else {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Allies<reset>: <gray>None"));
        }

        // Display enemies
        if (!enemies.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Enemies<reset>: <red>" + enemies.size()));
            for (String enemyGuildId : enemies) {
                Guild enemyGuild = guildService.getGuildById(enemyGuildId);
                if (enemyGuild != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <gray>• <red>" + enemyGuild.getName()));
                }
            }
        } else {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Enemies<reset>: <gray>None"));
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

        player.sendMessage(MessageFormatter.deserialize("<yellow>Region Limits<reset>:"));
        for (RegionTypeLimit limit : limits) {
            long usage = subregionService.getTypeUsage(guild.getId(), limit.typeId());
            long max = limit.maxTotalVolume();
            double percent = max > 0 ? (usage * 100.0) / max : 0;

            String displayName = subregionService.getTypeRegistry().getType(limit.typeId())
                    .map(SubregionType::getDisplayName)
                    .orElse(limit.typeId());

            String color = percent >= 90 ? "<red>" : percent >= 70 ? "<yellow>" : "<green>";
            player.sendMessage(MessageFormatter.deserialize(
                    "  <gray>• <gold>" + displayName + "<reset>: " + color +
                    formatNumber(usage) + "<gray>/<white>" + formatNumber(max) +
                    " <gray>(" + String.format("%.0f", percent) + "%)"));
        }
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
