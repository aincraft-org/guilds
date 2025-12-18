package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for joining a guild.
 */
public class JoinComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;

    @Inject
    public JoinComponent(GuildLifecycleService lifecycleService, GuildMemberService memberService) {
        this.lifecycleService = lifecycleService;
        this.memberService = memberService;
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public String getPermission() {
        return "guilds.join";
    }

    @Override
    public String getUsage() {
        return "/g join <guild-name>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to join guilds"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + guildName + "' not found"));
            return true;
        }

        // Check if guild is private
        if (!guild.isPublic()) {
            player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR,
                "✗ " + guild.getName() + " is private. You need an invite to join"
            ));
            return true;
        }

        if (memberService.joinGuild(guild.getId(), player.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ You joined '<gold>" + guild.getName() + "</gold>'!</green>"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to join guild. You may already be in a guild or the guild is full"));
        return true;
    }
}
