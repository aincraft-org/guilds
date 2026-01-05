package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return false;
        }

        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        // Check if guild is private
        if (!guild.isPublic()) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (memberService.joinGuild(guild.getId(), player.getUniqueId())) {
            Messages.send(player, MessageKey.GUILD_JOINED, guild.getName());
            return true;
        }

        Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
        return true;
    }
}
