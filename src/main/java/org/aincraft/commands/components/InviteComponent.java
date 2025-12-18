package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.InviteResult;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can invite others to guilds"));
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use this command"));
            return true;
        }

        // Validate args
        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
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
                player.sendMessage(MessageFormatter.format(
                    "<green>✓ Invited <gold>" + target.getName() + "</gold> to your guild (expires in 5 minutes)</green>"
                ));
                // Notify target if online
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(MessageFormatter.format(
                        "<green>You've been invited to join <gold>" + guild.getName() + "</gold>!</green>"
                    ));
                    onlineTarget.sendMessage(MessageFormatter.format(
                        "<gray>Use <gold>/g accept " + guild.getName() + "</gold> to accept or <gold>/g decline " + guild.getName() + "</gold> to decline</gray>"
                    ));
                }
            }
            case NO_PERMISSION -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "You don't have permission to invite players to this guild"
            ));
            case ALREADY_INVITED -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, target.getName() + " already has a pending invite to your guild"
            ));
            case INVITEE_IN_GUILD -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, target.getName() + " is already in a guild"
            ));
            case GUILD_FULL -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "Your guild is full"
            ));
            case INVITE_LIMIT_REACHED -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "Your guild has reached the maximum number of pending invites (10)"
            ));
            case TARGET_NOT_FOUND -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "Player not found: " + targetName
            ));
            case FAILURE -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "Failed to send invite: " + result.getReason()
            ));
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
