package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.LeaveResult;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to leave guilds"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        LeaveResult result = memberService.leaveGuild(guild.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ You left '<gold>" + guild.getName() + "</gold>'</green>"));
            return true;
        }

        // Display verbose error message
        String errorMsg = switch (result.getStatus()) {
            case OWNER_CANNOT_LEAVE -> "✗ " + result.getReason();
            case NOT_IN_GUILD -> "✗ " + result.getReason();
            case FAILURE -> "✗ Failed to leave guild: " + result.getReason();
            default -> "✗ Failed to leave guild";
        };

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, errorMsg));
        return true;
    }
}
