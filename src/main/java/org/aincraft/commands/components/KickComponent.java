package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NOT_GUILD_OWNER);
            return true;
        }

        OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            Messages.send(player, MessageKey.ERROR_PLAYER_NOT_FOUND, args[1]);
            return true;
        }

        // Check if trying to kick yourself
        if (player.getUniqueId().equals(target.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Check if target is guild owner
        if (guild.isOwner(target.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Check if target has ADMIN permission (only owner can kick admins)
        if (!guild.isOwner(player.getUniqueId()) &&
            permissionService.hasPermission(guild.getId(), target.getUniqueId(), org.aincraft.GuildPermission.ADMIN)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (memberService.kickMember(guild.getId(), player.getUniqueId(), target.getUniqueId())) {
            Messages.send(player, MessageKey.GUILD_KICKED, target.getName());

            // Notify the kicked player if they are online
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                Messages.send(onlineTarget, MessageKey.GUILD_KICKED, guild.getName());
            }

            return true;
        }

        Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
        return true;
    }
}
