package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for leaving a guild.
 */
public class LeaveComponent implements GuildCommand {
    private final GuildService guildService;

    public LeaveComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getPermission() {
        return "guilds.leave";
    }

    @Override
    public String getUsage() {
        return "/g leave";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to leave guilds"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        if (guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild owners cannot leave. Delete the guild or transfer ownership"));
            return true;
        }

        if (guildService.leaveGuild(guild.getId(), player.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ You left '<gold>" + guild.getName() + "</gold>'</green>"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to leave guild"));
        return true;
    }
}
