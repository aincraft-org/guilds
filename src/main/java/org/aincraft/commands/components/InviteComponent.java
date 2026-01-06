package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.InviteResult;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to invite players</error>");
            return true;
        }

        // Validate args
        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: <accent>/g invite <player></accent></error>");
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
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
                Mint.sendMessage(player, "<success>Invite sent to <secondary>" + target.getName() + "</secondary></success>");
                // Notify target if online
                if (onlineTarget != null) {
                    Mint.sendMessage(onlineTarget, "<info><secondary>" + player.getName() + "</secondary> invited you to join <secondary>" + guild.getName() + "</secondary></info>");
                }
            }
            case NO_PERMISSION -> Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to invite players</error>");
            case ALREADY_INVITED -> Mint.sendMessage(player, "<warning><secondary>" + target.getName() + "</secondary> already has a pending invite to your guild</warning>");
            case INVITEE_IN_GUILD -> Mint.sendMessage(player, "<warning><secondary>" + target.getName() + "</secondary> is already in a guild</warning>");
            case GUILD_FULL -> Mint.sendMessage(player, "<error>Guild is full (reached <accent>max members</accent>)</error>");
            case INVITE_LIMIT_REACHED -> Mint.sendMessage(player, "<warning>Your guild has reached the <accent>maximum</accent> number of pending invites (<accent>10</accent>)</warning>");
            case TARGET_NOT_FOUND -> Mint.sendMessage(player, "<error>Player not found: <secondary>" + targetName + "</secondary></error>");
            case FAILURE -> Mint.sendMessage(player, "<error>" + result.getReason() + "</error>");
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
