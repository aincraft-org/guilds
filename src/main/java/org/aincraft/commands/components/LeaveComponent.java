package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.LeaveResult;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for leaving a guild.
 */
public class LeaveComponent implements GuildCommand {
    private final GuildMemberService memberService;

    @Inject
    public LeaveComponent(GuildMemberService memberService) {
        this.memberService = memberService;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION, "leave guilds");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        LeaveResult result = memberService.leaveGuild(guild.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            Messages.send(player, MessageKey.GUILD_LEFT, guild.getName());
            return true;
        }

        // Display verbose error message
        String errorMsg = switch (result.getStatus()) {
            case OWNER_CANNOT_LEAVE -> result.getReason();
            case NOT_IN_GUILD -> result.getReason();
            case FAILURE -> result.getReason();
            default -> "Failed to leave guild";
        };

        Messages.send(player, MessageKey.ERROR_NO_PERMISSION, "leave guilds");
        return true;
    }
}
