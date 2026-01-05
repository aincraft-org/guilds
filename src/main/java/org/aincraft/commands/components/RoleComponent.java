package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.role.gui.RoleCreationGUI;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.GuildRoleService;
import org.aincraft.service.PermissionService;
import org.aincraft.storage.MemberRoleRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for managing guild roles.
 * Commands:
 * - `/g role editor <name>` - Open role editor GUI (creates if doesn't exist, edits if exists)
 * - `/g role create <name> <perms> <priority>` - Create role directly (verbose)
 * - `/g role copy <source> <new_name>` - Copy role from source role
 * - `/g role delete <name>` - Delete role
 * - `/g role perm <name> <perm> <true|false>` - Set permission
 * - `/g role priority <name> <priority>` - Set role priority
 * - `/g role list` - List all roles
 * - `/g role info <name>` - Show role information
 * - `/g role assign <player> <role>` - Assign role to player
 * - `/g role unassign <player> <role>` - Unassign role from player
 */
public final class RoleComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildRoleService roleService;
    private final PermissionService permissionService;
    private final MemberRoleRepository memberRoleRepository;

    @Inject
    public RoleComponent(GuildMemberService memberService, GuildRoleService roleService,
                        PermissionService permissionService, MemberRoleRepository memberRoleRepository) {
        this.memberService = memberService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.memberRoleRepository = memberRoleRepository;
    }

    @Override
    public String getName() {
        return "role";
    }

    @Override
    public String getPermission() {
        return "guilds.role";
    }

    @Override
    public String getUsage() {
        return "/g role <editor|create|copy|delete|perm|priority|list|info|assign|unassign>";
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

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        if (args.length < 2) {
            showRoleUsage(player);
            return true;
        }

        String subCommand = args[1].toLowerCase();

        return switch (subCommand) {
            case "editor" -> handleEditor(player, guild, args);
            case "create" -> handleCreate(player, guild, args);
            case "copy" -> handleCopy(player, guild, args);
            case "delete" -> handleDelete(player, guild, args);
            case "perm" -> handleSetPerm(player, guild, args);
            case "priority" -> handleSetPriority(player, guild, args);
            case "list" -> handleList(player, guild);
            case "info" -> handleInfo(player, guild, args);
            case "assign" -> handleAssign(player, guild, args);
            case "unassign" -> handleUnassign(player, guild, args);
            default -> {
                showRoleUsage(player);
                yield true;
            }
        };
    }

    private void showRoleUsage(Player player) {
        Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
    }

    /**
     * Handles role editor - opens GUI for creating new role or editing existing one.
     */
    private boolean handleEditor(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role editor <name>");
            return true;
        }

        String name = args[2];

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
            return true;
        }

        // Open editor GUI (will create if doesn't exist, edit if exists)
        RoleCreationGUI gui = new RoleCreationGUI(guild, player, name, roleService, permissionService);
        gui.open();
        return true;
    }

    /**
     * Handles direct role creation with all parameters.
     */
    private boolean handleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role create <name> <perms> <priority>");
            return true;
        }

        String name = args[2];

        // Check if role name already exists
        if (roleService.getRoleByName(guild.getId(), name) != null) {
            Messages.send(player, MessageKey.ROLE_ALREADY_EXISTS, name);
            return true;
        }

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
            return true;
        }

        int permissions = parsePermissions(args[3]);
        if (permissions == -1) {
            Messages.send(player, MessageKey.ROLE_INVALID_PERMISSIONS);
            return true;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            Messages.send(player, MessageKey.ROLE_INVALID_PRIORITY);
            return true;
        }

        GuildRole role = roleService.createRole(guild.getId(), player.getUniqueId(), name, permissions, priority);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
            return true;
        }

        Messages.send(player, MessageKey.ROLE_CREATED, name);
        return true;
    }

    /**
     * Handles role copy - copies permissions and priority from source role to new role.
     */
    private boolean handleCopy(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role copy <source_role> <new_role_name>");
            return true;
        }

        String sourceName = args[2];
        String newName = args[3];

        // Validate that source and target names are different
        if (sourceName.equalsIgnoreCase(newName)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Check if source role exists
        GuildRole sourceRole = roleService.getRoleByName(guild.getId(), sourceName);
        if (sourceRole == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, sourceName);
            return true;
        }

        // Check if target role name already exists
        if (roleService.getRoleByName(guild.getId(), newName) != null) {
            Messages.send(player, MessageKey.ROLE_ALREADY_EXISTS, newName);
            return true;
        }

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
            return true;
        }

        // Perform the copy
        GuildRole copiedRole = roleService.copyRole(guild.getId(), player.getUniqueId(), sourceName, newName);
        if (copiedRole == null) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        Messages.send(player, MessageKey.ROLE_COPIED, sourceName, newName);
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<GuildRole> roles = roleService.getGuildRoles(guild.getId());

        // Send header
        player.sendMessage(Component.text("Guild Roles (sorted by priority)", NamedTextColor.GOLD));
        if (roles.isEmpty()) {
            player.sendMessage(Component.text("No roles defined", NamedTextColor.GRAY));
        } else {
            for (GuildRole role : roles) {
                String priorityBadge = role.getPriority() > 0 ? "[" + role.getPriority() + "] " : "";
                player.sendMessage(Component.text(priorityBadge + role.getName() + " [" + role.getPermissions() + "]", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private boolean handleInfo(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role info <name>");
            return true;
        }

        String name = args[2];
        GuildRole role = roleService.getRoleByName(guild.getId(), name);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, name);
            return true;
        }

        displayEnhancedRoleInfo(player, guild, role);
        return true;
    }

    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role delete <name>");
            return true;
        }

        String name = args[2];
        GuildRole role = roleService.getRoleByName(guild.getId(), name);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, name);
            return true;
        }

        if (roleService.deleteRole(guild.getId(), player.getUniqueId(), role.getId())) {
            Messages.send(player, MessageKey.ROLE_DELETED, name);
        } else {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
        }
        return true;
    }

    private boolean handleSetPerm(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role perm <role> <permission> <true|false>");
            return true;
        }

        String roleName = args[2];
        String permName = args[3].toUpperCase();
        boolean grant = args[4].equalsIgnoreCase("true");

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, roleName);
            return true;
        }

        GuildPermission perm;
        try {
            perm = GuildPermission.valueOf(permName);
        } catch (IllegalArgumentException e) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        int newPermissions = role.getPermissions();
        if (grant) {
            newPermissions |= perm.getBit();
        } else {
            newPermissions &= ~perm.getBit();
        }

        if (roleService.updateRolePermissions(guild.getId(), player.getUniqueId(), role.getId(), newPermissions)) {
            Messages.send(player, grant ? MessageKey.ROLE_PERMISSION_ADDED : MessageKey.ROLE_PERMISSION_REMOVED, permName, roleName);
        } else {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
        }
        return true;
    }

    private boolean handleSetPriority(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role priority <role> <priority>");
            return true;
        }

        String roleName = args[2];
        int priority;

        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            Messages.send(player, MessageKey.ROLE_INVALID_PRIORITY);
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, roleName);
            return true;
        }

        // Check permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
            return true;
        }

        role.setPriority(priority);
        roleService.saveRole(role);

        Messages.send(player, MessageKey.ROLE_PRIORITY_SET, roleName, priority);
        return true;
    }

    private boolean handleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role assign <player> <role>");
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Messages.send(player, MessageKey.ERROR_PLAYER_NOT_FOUND, targetName);
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, roleName);
            return true;
        }

        if (roleService.assignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            Messages.send(player, MessageKey.ROLE_ASSIGNED, roleName, target.getName());
            Messages.send(target, MessageKey.ROLE_ASSIGNED, roleName, guild.getName());
        } else {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
        }
        return true;
    }

    private boolean handleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g role unassign <player> <role>");
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Messages.send(player, MessageKey.ERROR_PLAYER_NOT_FOUND, targetName);
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Messages.send(player, MessageKey.ROLE_NOT_FOUND, roleName);
            return true;
        }

        if (roleService.unassignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            Messages.send(player, MessageKey.ROLE_UNASSIGNED, roleName, target.getName());
            Messages.send(target, MessageKey.ROLE_UNASSIGNED, roleName, guild.getName());
        } else {
            Messages.send(player, MessageKey.ROLE_NO_PERMISSION);
        }
        return true;
    }

    /**
     * Parses permission string as integer.
     */
    private int parsePermissions(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Format strings for role info display
    private static final String ROLE_HEADER = "  <dark_gray>o<gold>0<yellow>o<gold>.<left_underline><dark_gray>[ <white><role_name></white> <dark_gray>]<right_underline><yellow>o<gold>0<dark_gray>o";
    private static final String ROLE_PRIORITY_WITH_VALUE = "  <yellow>Priority: <dark_gray>[<gold><priority></gold>]</dark_gray>";
    private static final String ROLE_PRIORITY_DEFAULT = "  <yellow>Priority: <gray>0 (default)";
    private static final String ROLE_PERMISSIONS_HEADER = "  <yellow>Permissions:";
    private static final String ROLE_PERMISSIONS_NONE = "    <dark_gray>• <gray>None";
    private static final String ROLE_PERMISSION_ITEM = "    <dark_gray>• <green><permission>";
    private static final String ROLE_MEMBERS_HEADER = "  <yellow>Members <dark_gray>(<white><member_count></white>):";
    private static final String ROLE_MEMBERS_NONE = "    <gray>No members assigned";
    private static final String ROLE_MEMBER_ITEM = "    <dark_gray>• <<status_color>><member_name>";
    private static final String ROLE_MEMBERS_MORE = "    <dark_gray>...and <white><more_count></white> more";

    /**
     * Displays enhanced role information with rich formatting.
     */
    private void displayEnhancedRoleInfo(Player player, Guild guild, GuildRole role) {
        // Header
        String roleName = role.getName();
        int totalWidth = 40;
        int sideLength = (totalWidth - roleName.length() - 4) / 2;
        String underline = "_".repeat(Math.max(0, sideLength - 4));

        player.sendMessage(Messages.provider().getWithResolvers(MessageKey.LIST_HEADER,
                Placeholder.component("role_name", Component.text(roleName)),
                Placeholder.component("left_underline", Component.text(underline)),
                Placeholder.component("right_underline", Component.text(underline))));
        player.sendMessage(Component.empty());

        // Priority
        if (role.getPriority() > 0) {
            player.sendMessage(Messages.provider().getWithResolvers(MessageKey.ROLE_PRIORITY_SET,
                    Placeholder.component("priority", Component.text(role.getPriority()))));
        } else {
            player.sendMessage(Component.text("Priority: 0 (default)", NamedTextColor.YELLOW));
        }

        // Permissions
        player.sendMessage(Component.text("Permissions:", NamedTextColor.YELLOW));
        if (role.getPermissions() == 0) {
            player.sendMessage(Component.text("  • None", NamedTextColor.DARK_GRAY));
        } else {
            for (GuildPermission perm : GuildPermission.values()) {
                if (role.hasPermission(perm)) {
                    player.sendMessage(Component.text("  • " + perm.name(), NamedTextColor.GREEN));
                }
            }
        }

        // Bitfield with hover
        Component bitfieldLine = Component.text()
                .append(Component.text("  Bitfield: ", NamedTextColor.YELLOW))
                .append(Component.text(String.valueOf(role.getPermissions()), NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text()
                                .append(Component.text("Permission Bitfield", NamedTextColor.YELLOW))
                                .append(Component.newline())
                                .append(Component.text("Raw value: ", NamedTextColor.GRAY))
                                .append(Component.text(String.valueOf(role.getPermissions()), NamedTextColor.WHITE))
                                .build())))
                .build();
        player.sendMessage(bitfieldLine);
        player.sendMessage(Component.empty());

        // Members
        List<UUID> memberIds = memberRoleRepository.getMembersWithRole(role.getId());
        player.sendMessage(Component.text("Members (" + memberIds.size() + "):", NamedTextColor.YELLOW));

        if (memberIds.isEmpty()) {
            player.sendMessage(Component.text("No members assigned", NamedTextColor.GRAY));
        } else {
            int displayLimit = 10;
            for (int i = 0; i < Math.min(displayLimit, memberIds.size()); i++) {
                UUID memberId = memberIds.get(i);
                var offlinePlayer = Bukkit.getOfflinePlayer(memberId);
                String memberName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
                NamedTextColor statusColor = offlinePlayer.isOnline() ? NamedTextColor.GREEN : NamedTextColor.GRAY;

                player.sendMessage(Component.text("  • " + memberName, statusColor));
            }
            if (memberIds.size() > displayLimit) {
                player.sendMessage(Component.text("  ...and " + (memberIds.size() - displayLimit) + " more", NamedTextColor.DARK_GRAY));
            }
        }

        player.sendMessage(Component.empty());

        // 6. Creation metadata with hover (only if non-null)
        if (role.getCreatedBy() != null && role.getCreatedAt() != null) {
            var creatorPlayer = Bukkit.getOfflinePlayer(role.getCreatedBy());
            String creatorName = creatorPlayer.getName() != null ? creatorPlayer.getName() : "Unknown";

            String dateOnly = new SimpleDateFormat("yyyy-MM-dd").format(new Date(role.getCreatedAt()));
            String fullDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date(role.getCreatedAt()));
            long daysAgo = (System.currentTimeMillis() - role.getCreatedAt()) / (1000 * 60 * 60 * 24);

            Component createdByLine = Component.text()
                .append(Component.text("  Created by: ", NamedTextColor.YELLOW))
                .append(Component.text(creatorName, NamedTextColor.WHITE)
                    .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text("Creator: ", NamedTextColor.YELLOW))
                        .append(Component.text(creatorName, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("UUID: ", NamedTextColor.YELLOW))
                        .append(Component.text(role.getCreatedBy().toString(), NamedTextColor.GRAY))
                        .build())))
                .build();
            player.sendMessage(createdByLine);

            Component createdAtLine = Component.text()
                .append(Component.text("  Created on: ", NamedTextColor.YELLOW))
                .append(Component.text(dateOnly, NamedTextColor.WHITE)
                    .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text("Created: ", NamedTextColor.YELLOW))
                        .append(Component.text(fullDateTime, NamedTextColor.WHITE))
                        .append(Component.newline())
                        .append(Component.text("Days ago: ", NamedTextColor.YELLOW))
                        .append(Component.text(String.valueOf(daysAgo), NamedTextColor.GRAY))
                        .build())))
                .build();
            player.sendMessage(createdAtLine);

            player.sendMessage(Component.empty());
        }
    }

    /**
     * Formats permission bitfield as human-readable string.
     */
    private String formatPermissions(int permissions) {
        if (permissions == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (GuildPermission perm : GuildPermission.values()) {
            if ((permissions & perm.getBit()) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(perm.name());
            }
        }
        return sb.toString();
    }
}
