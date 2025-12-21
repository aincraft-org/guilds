package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to kick from guilds"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can kick members"));
            return true;
        }

        OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Player not found"));
            return true;
        }

        // Check if trying to kick yourself
        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot kick yourself"));
            return true;
        }

        // Check if target is guild owner
        if (guild.isOwner(target.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot kick the guild owner"));
            return true;
        }

        // Check if target has ADMIN permission (only owner can kick admins)
        if (!guild.isOwner(player.getUniqueId()) &&
            permissionService.hasPermission(guild.getId(), target.getUniqueId(), org.aincraft.GuildPermission.ADMIN)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can kick administrators"));
            return true;
        }

        if (memberService.kickMember(guild.getId(), player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ <gold>" + target.getName() + "</gold> was kicked from the guild</green>"));

            // Notify the kicked player if they are online
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                onlineTarget.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You were kicked from " + guild.getName()));
            }

            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to kick member. You may lack permission or they have a higher role than you"));
        return true;
    }
}
