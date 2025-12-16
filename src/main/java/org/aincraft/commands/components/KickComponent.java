package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for kicking a member from a guild.
 */
public class KickComponent implements GuildCommand {
    private final GuildService guildService;

    public KickComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getPermission() {
        return "guilds.kick";
    }

    @Override
    public String getUsage() {
        return "/g kick <player-name>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to kick from guilds"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can kick members"));
            return true;
        }

        OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Player not found"));
            return true;
        }

        if (guildService.kickMember(guild.getId(), player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ <gold>" + target.getName() + "</gold> was kicked from the guild</green>"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to kick member. They may not be in your guild"));
        return true;
    }
}
