package org.aincraft.commands.components;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.storage.MemberRoleRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Component for checking member information including roles, permissions, and join date.
 */
public class MemberComponent implements GuildCommand {
    private final GuildService guildService;
    private final MemberRoleRepository memberRoleRepository;

    public MemberComponent(GuildService guildService, MemberRoleRepository memberRoleRepository) {
        this.guildService = guildService;
        this.memberRoleRepository = memberRoleRepository;
    }

    @Override
    public String getName() {
        return "member";
    }

    @Override
    public String getPermission() {
        return "guilds.member";
    }

    @Override
    public String getUsage() {
        return "/g member [player]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to check member info"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        UUID targetId = player.getUniqueId();

        // If a player name is provided, look them up
        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found: " + args[1]));
                return true;
            }

            targetId = target.getUniqueId();

            // Check if target is in the same guild
            Guild targetGuild = guildService.getPlayerGuild(targetId);
            if (targetGuild == null || !targetGuild.getId().equals(guild.getId())) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "That player is not in your guild"));
                return true;
            }
        }

        displayMemberInfo(player, guild, targetId);
        return true;
    }

    private void displayMemberInfo(Player sender, Guild guild, UUID targetId) {
        String targetName = Bukkit.getOfflinePlayer(targetId).getName();
        if (targetName == null) {
            targetName = targetId.toString();
        }

        sender.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Member Info: " + targetName, ""));

        // Show owner status
        if (guild.isOwner(targetId)) {
            sender.sendMessage(MessageFormatter.deserialize("<gold>★ Guild Owner <gray>(Full permissions)"));
            return;
        }

        // Show online/offline status
        var offlinePlayer = Bukkit.getOfflinePlayer(targetId);
        boolean isOnline = offlinePlayer.isOnline();
        String statusColor = isOnline ? "<green>" : "<gray>";
        String statusText = isOnline ? "Online" : "Offline";
        sender.sendMessage(MessageFormatter.deserialize("<yellow>Status<reset>: " + statusColor + statusText));

        // Show join date
        Optional<Long> joinDateOpt = guildService.getMemberJoinDate(guild.getId(), targetId);
        if (joinDateOpt.isPresent()) {
            Component joinDateLine = Component.text()
                .append(Component.text("Joined", NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(createHoverableJoinDate(joinDateOpt.get()))
                .build();
            sender.sendMessage(joinDateLine);
        } else {
            sender.sendMessage(MessageFormatter.deserialize("<yellow>Joined<reset>: <gray>Unknown"));
        }

        sender.sendMessage(Component.empty()); // Spacing

        // Get member's roles
        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guild.getId(), targetId);

        if (roleIds.isEmpty()) {
            sender.sendMessage(MessageFormatter.deserialize("<gray>No roles assigned"));
        } else {
            sender.sendMessage(MessageFormatter.deserialize("<yellow>Roles:"));
            for (String roleId : roleIds) {
                GuildRole role = guildService.getRoleById(roleId);
                if (role != null) {
                    String priorityBadge = role.getPriority() > 0 ? "<dark_gray>[<gold>" + role.getPriority() + "</gold>]</dark_gray> " : "";
                    sender.sendMessage(MessageFormatter.deserialize(priorityBadge + "  <gray>• <yellow>" + role.getName()));
                }
            }
        }

        // Show effective permissions (from highest priority role)
        int effectivePermissions = 0;
        for (String roleId : roleIds) {
            GuildRole role = guildService.getRoleById(roleId);
            if (role != null) {
                effectivePermissions |= role.getPermissions();
            }
        }

        sender.sendMessage(MessageFormatter.deserialize("<yellow>Permissions:"));
        if (effectivePermissions == 0) {
            sender.sendMessage(MessageFormatter.deserialize("  <gray>None"));
        } else {
            for (GuildPermission perm : GuildPermission.values()) {
                if ((effectivePermissions & perm.getBit()) != 0) {
                    sender.sendMessage(MessageFormatter.deserialize("  <gray>• <green>" + perm.name()));
                }
            }
        }
    }

    private Component createHoverableJoinDate(long timestamp) {
        String dateOnly = new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp));
        String fullDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date(timestamp));
        long daysAgo = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24);

        // Build hover tooltip
        Component tooltip = Component.text()
            .append(Component.text("Joined: ", NamedTextColor.YELLOW))
            .append(Component.text(fullDateTime, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Days ago: ", NamedTextColor.YELLOW))
            .append(Component.text(String.valueOf(daysAgo), NamedTextColor.GRAY))
            .build();

        return Component.text(dateOnly, NamedTextColor.WHITE)
            .hoverEvent(tooltip);
    }
}
