package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.InviteResult;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for inviting players to a guild.
 */
public class InviteComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildMemberService memberService;

    @Inject
    public InviteComponent(InviteService inviteService, GuildMemberService memberService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Validate args
        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Get target player
        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Try to get online player for better accuracy
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null) {
            target = onlineTarget;
        }

        // Send invite
        InviteResult result = inviteService.sendInvite(guild.getId(), player.getUniqueId(), target.getUniqueId());

        // Handle result
        switch (result.getStatus()) {
            case SUCCESS -> {
                Messages.send(player, MessageKey.INVITE_SENT, target.getName());
                // Notify target if online
                if (onlineTarget != null) {
                    Messages.send(onlineTarget, MessageKey.INVITE_RECEIVED, guild.getName());
                }
            }
            case NO_PERMISSION -> Messages.send(player, MessageKey.INVITE_NO_PERMISSION);
            case ALREADY_INVITED -> Messages.send(player, MessageKey.INVITE_ALREADY_PENDING, target.getName());
            case INVITEE_IN_GUILD -> Messages.send(player, MessageKey.INVITE_TARGET_IN_GUILD, target.getName());
            case GUILD_FULL -> Messages.send(player, MessageKey.ERROR_GUILD_FULL);
            case INVITE_LIMIT_REACHED -> Messages.send(player, MessageKey.INVITE_MAX_PENDING);
            case TARGET_NOT_FOUND -> Messages.send(player, MessageKey.ERROR_PLAYER_NOT_FOUND, targetName);
            case FAILURE -> Messages.send(player, MessageKey.ERROR_NO_PERMISSION, result.getReason());
        }

        return true;
    }

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public String getPermission() {
        return "guilds.invite";
    }

    @Override
    public String getUsage() {
        return "/g invite <player>";
    }
}
