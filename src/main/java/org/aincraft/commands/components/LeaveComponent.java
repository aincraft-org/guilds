package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.LeaveResult;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to leave guilds</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        LeaveResult result = memberService.leaveGuild(guild.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            Mint.sendMessage(player, "<success>You have left <secondary>" + guild.getName() + "</secondary></success>");
            return true;
        }

        // Display verbose error message
        String errorMsg = switch (result.getStatus()) {
            case OWNER_CANNOT_LEAVE -> result.getReason();
            case NOT_IN_GUILD -> result.getReason();
            case FAILURE -> result.getReason();
            default -> "Failed to leave guild";
        };

        Mint.sendMessage(player, "<error>You don't have permission to leave guilds</error>");
        return true;
    }
}
