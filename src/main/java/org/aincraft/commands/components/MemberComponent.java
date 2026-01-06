package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.GuildRoleService;
import org.aincraft.storage.MemberRoleRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for checking member information including roles, permissions, and join date.
 */
public class MemberComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildRoleService roleService;
    private final MemberRoleRepository memberRoleRepository;

    @Inject
    public MemberComponent(GuildMemberService memberService, GuildRoleService roleService,
                          MemberRoleRepository memberRoleRepository) {
        this.memberService = memberService;
        this.roleService = roleService;
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
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use this command</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        UUID targetId = player.getUniqueId();

        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Mint.sendMessage(player, "<error>Player not found: <secondary>" + args[1] + "</secondary></error>");
                return true;
            }

            targetId = target.getUniqueId();

            Guild targetGuild = memberService.getPlayerGuild(targetId);
            if (targetGuild == null || !targetGuild.getId().equals(guild.getId())) {
                Mint.sendMessage(player, "<error>You are not in a guild</error>");
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

        Mint.sendMessage(sender, "<info>=== " + targetName + " ===</info>");

        // Show owner status
        if (guild.isOwner(targetId)) {
            Mint.sendMessage(sender, "<secondary>★ Guild Owner <neutral>(Full permissions)");
            return;
        }

        // Show online/offline status
        var offlinePlayer = Bukkit.getOfflinePlayer(targetId);
        boolean isOnline = offlinePlayer.isOnline();
        String statusColor = isOnline ? "<success>" : "<neutral>";
        String statusText = isOnline ? "Online" : "Offline";
        Mint.sendMessage(sender, "<neutral>Status<reset>: " + statusColor + statusText);

        // Show join date
        Optional<Long> joinDateOpt = memberService.getMemberJoinDate(guild.getId(), targetId);
        if (joinDateOpt.isPresent()) {
            Component joinDateLine = Component.text()
                .append(Component.text("Joined", NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(createHoverableJoinDate(joinDateOpt.get()))
                .build();
            sender.sendMessage(joinDateLine);
        } else {
            Mint.sendMessage(sender, "<neutral>Joined<reset>: <neutral>Unknown");
        }

        sender.sendMessage(Component.empty()); // Spacing

        // Get member's roles
        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guild.getId(), targetId);

        if (roleIds.isEmpty()) {
            Mint.sendMessage(sender, "<neutral>No roles assigned");
        } else {
            Mint.sendMessage(sender, "<warning>Roles:");
            for (String roleId : roleIds) {
                GuildRole role = roleService.getRoleByIdAndGuild(roleId, guild.getId()).orElse(null);
                if (role != null) {
                    String priorityBadge = role.getPriority() > 0 ? "<neutral>[<secondary>" + role.getPriority() + "</secondary>]</neutral> " : "";
                    Mint.sendMessage(sender, priorityBadge + "  <neutral>• <warning>" + role.getName());
                }
            }
        }

        // Show effective permissions (from highest priority role)
        int effectivePermissions = 0;
        for (String roleId : roleIds) {
            GuildRole role = roleService.getRoleByIdAndGuild(roleId, guild.getId()).orElse(null);
            if (role != null) {
                effectivePermissions |= role.getPermissions();
            }
        }

        Mint.sendMessage(sender, "<warning>Permissions:");
        if (effectivePermissions == 0) {
            Mint.sendMessage(sender, "  <neutral>None");
        } else {
            for (GuildPermission perm : GuildPermission.values()) {
                if ((effectivePermissions & perm.getBit()) != 0) {
                    Mint.sendMessage(sender, "  <neutral>• <success>" + perm.name());
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
