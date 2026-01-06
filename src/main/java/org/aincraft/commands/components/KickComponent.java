package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for kicking a member from a guild.
 */
public class KickComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;

    @Inject
    public KickComponent(GuildMemberService memberService, PermissionService permissionService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to kick members</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: /g kick <player-name></error>");
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only the guild owner can do this</error>");
            return true;
        }

        OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            Mint.sendMessage(player, "<error>Player not found: <secondary>" + args[1] + "</secondary></error>");
            return true;
        }

        // Check if trying to kick yourself
        if (player.getUniqueId().equals(target.getUniqueId())) {
            Mint.sendMessage(player, "<error>You cannot kick yourself</error>");
            return true;
        }

        // Check if target is guild owner
        if (guild.isOwner(target.getUniqueId())) {
            Mint.sendMessage(player, "<error>You cannot kick the guild owner</error>");
            return true;
        }

        // Check if target has ADMIN permission (only owner can kick admins)
        if (!guild.isOwner(player.getUniqueId()) &&
            permissionService.hasPermission(guild.getId(), target.getUniqueId(), org.aincraft.GuildPermission.ADMIN)) {
            Mint.sendMessage(player, "<error>You cannot kick this member</error>");
            return true;
        }

        if (memberService.kickMember(guild.getId(), player.getUniqueId(), target.getUniqueId())) {
            Mint.sendMessage(player, "<success><secondary>" + target.getName() + "</secondary> has been kicked from the guild</success>");

            // Notify the kicked player if they are online
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                Mint.sendMessage(onlineTarget, "<warning>You have been kicked from <secondary>" + guild.getName() + "</secondary></warning>");
            }

            return true;
        }

        Mint.sendMessage(player, "<error>You don't have permission to kick this member</error>");
        return true;
    }
}
