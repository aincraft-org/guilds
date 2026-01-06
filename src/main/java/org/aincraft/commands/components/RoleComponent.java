package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
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
        Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
    }

    /**
     * Handles role editor - opens GUI for creating new role or editing existing one.
     */
    private boolean handleEditor(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g role editor <name></error>");
            return true;
        }

        String name = args[2];

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Mint.sendMessage(player, "<error>No permission</error>");
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
            Mint.sendMessage(player, "<error>Usage: /g role create <name> <perms> <priority></error>");
            return true;
        }

        String name = args[2];

        // Check if role name already exists
        if (roleService.getRoleByName(guild.getId(), name) != null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + name + "</secondary> already exists</error>");
            return true;
        }

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        int permissions = parsePermissions(args[3]);
        if (permissions == -1) {
            Mint.sendMessage(player, "<error>Invalid permissions</error>");
            return true;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            Mint.sendMessage(player, "<error>Invalid priority</error>");
            return true;
        }

        GuildRole role = roleService.createRole(guild.getId(), player.getUniqueId(), name, permissions, priority);
        if (role == null) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        Mint.sendMessage(player, "<success>Role <secondary>" + name + "</secondary> created</success>");
        return true;
    }

    /**
     * Handles role copy - copies permissions and priority from source role to new role.
     */
    private boolean handleCopy(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g role copy <source_role> <new_role_name></error>");
            return true;
        }

        String sourceName = args[2];
        String newName = args[3];

        // Validate that source and target names are different
        if (sourceName.equalsIgnoreCase(newName)) {
            Mint.sendMessage(player, "<error>Names must be different</error>");
            return true;
        }

        // Check if source role exists
        GuildRole sourceRole = roleService.getRoleByName(guild.getId(), sourceName);
        if (sourceRole == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + sourceName + "</secondary> not found</error>");
            return true;
        }

        // Check if target role name already exists
        if (roleService.getRoleByName(guild.getId(), newName) != null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + newName + "</secondary> already exists</error>");
            return true;
        }

        // Check MANAGE_ROLES permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        // Perform the copy
        GuildRole copiedRole = roleService.copyRole(guild.getId(), player.getUniqueId(), sourceName, newName);
        if (copiedRole == null) {
            Mint.sendMessage(player, "<error>Failed to copy role</error>");
            return true;
        }

        Mint.sendMessage(player, "<success>Role <secondary>" + sourceName + "</secondary> copied to <secondary>" + newName + "</secondary></success>");
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<GuildRole> roles = roleService.getGuildRoles(guild.getId());

        // Send header
        Mint.sendMessage(player, "<primary>=== Guild Roles (sorted by priority) ===</primary>");
        if (roles.isEmpty()) {
            Mint.sendMessage(player, "<neutral>No roles defined</neutral>");
        } else {
            for (GuildRole role : roles) {
                String priorityBadge = role.getPriority() > 0 ? "[" + role.getPriority() + "] " : "";
                Mint.sendMessage(player, "<secondary>" + priorityBadge + role.getName() + "</secondary> [" + role.getPermissions() + "]");
            }
        }
        return true;
    }

    private boolean handleInfo(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g role info <name></error>");
            return true;
        }

        String name = args[2];
        GuildRole role = roleService.getRoleByName(guild.getId(), name);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + name + "</secondary> not found</error>");
            return true;
        }

        displayEnhancedRoleInfo(player, guild, role);
        return true;
    }

    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g role delete <name></error>");
            return true;
        }

        String name = args[2];
        GuildRole role = roleService.getRoleByName(guild.getId(), name);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + name + "</secondary> not found</error>");
            return true;
        }

        if (roleService.deleteRole(guild.getId(), player.getUniqueId(), role.getId())) {
            Mint.sendMessage(player, "<success>Role <secondary>" + name + "</secondary> deleted</success>");
        } else {
            Mint.sendMessage(player, "<error>No permission</error>");
        }
        return true;
    }

    private boolean handleSetPerm(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Mint.sendMessage(player, "<error>Usage: /g role perm <role> <permission> <true|false></error>");
            return true;
        }

        String roleName = args[2];
        String permName = args[3].toUpperCase();
        boolean grant = args[4].equalsIgnoreCase("true");

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + roleName + "</secondary> not found</error>");
            return true;
        }

        GuildPermission perm;
        try {
            perm = GuildPermission.valueOf(permName);
        } catch (IllegalArgumentException e) {
            Mint.sendMessage(player, "<error>Invalid permission</error>");
            return true;
        }

        int newPermissions = role.getPermissions();
        if (grant) {
            newPermissions |= perm.getBit();
        } else {
            newPermissions &= ~perm.getBit();
        }

        if (roleService.updateRolePermissions(guild.getId(), player.getUniqueId(), role.getId(), newPermissions)) {
            if (grant) {
                Mint.sendMessage(player, "<success>Permission <primary>" + permName + "</primary> added to role <secondary>" + roleName + "</secondary></success>");
            } else {
                Mint.sendMessage(player, "<success>Permission <primary>" + permName + "</primary> removed from role <secondary>" + roleName + "</secondary></success>");
            }
        } else {
            Mint.sendMessage(player, "<error>No permission</error>");
        }
        return true;
    }

    private boolean handleSetPriority(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g role priority <role> <priority></error>");
            return true;
        }

        String roleName = args[2];
        int priority;

        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            Mint.sendMessage(player, "<error>Invalid priority</error>");
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + roleName + "</secondary> not found</error>");
            return true;
        }

        // Check permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        role.setPriority(priority);
        roleService.saveRole(role);

        Mint.sendMessage(player, "<success>Role <secondary>" + roleName + "</secondary> priority set to " + priority + "</success>");
        return true;
    }

    private boolean handleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g role assign <player> <role></error>");
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Mint.sendMessage(player, "<error>Player <primary>" + targetName + "</primary> not found</error>");
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + roleName + "</secondary> not found</error>");
            return true;
        }

        if (roleService.assignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            Mint.sendMessage(player, "<success>Role <secondary>" + roleName + "</secondary> assigned to <primary>" + target.getName() + "</primary></success>");
            Mint.sendMessage(target, "<success>Role <secondary>" + roleName + "</secondary> assigned in guild <primary>" + guild.getName() + "</primary></success>");
        } else {
            Mint.sendMessage(player, "<error>No permission</error>");
        }
        return true;
    }

    private boolean handleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g role unassign <player> <role></error>");
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Mint.sendMessage(player, "<error>Player <primary>" + targetName + "</primary> not found</error>");
            return true;
        }

        GuildRole role = roleService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            Mint.sendMessage(player, "<error>Role <secondary>" + roleName + "</secondary> not found</error>");
            return true;
        }

        if (roleService.unassignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            Mint.sendMessage(player, "<success>Role <secondary>" + roleName + "</secondary> unassigned from <primary>" + target.getName() + "</primary></success>");
            Mint.sendMessage(target, "<success>Role <secondary>" + roleName + "</secondary> unassigned from guild <primary>" + guild.getName() + "</primary></success>");
        } else {
            Mint.sendMessage(player, "<error>No permission</error>");
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

    /**
     * Displays enhanced role information with rich formatting.
     */
    private void displayEnhancedRoleInfo(Player player, Guild guild, GuildRole role) {
        // Header
        String roleName = role.getName();
        int totalWidth = 40;
        int sideLength = (totalWidth - roleName.length() - 4) / 2;
        String underline = "_".repeat(Math.max(0, sideLength - 4));

        Mint.sendMessage(player, "<primary>=== " + roleName + " ===</primary>");
        Mint.sendMessage(player, "<neutral></neutral>");

        // Priority
        if (role.getPriority() > 0) {
            Mint.sendMessage(player, "<warning>Priority: " + role.getPriority() + "</warning>");
        } else {
            Mint.sendMessage(player, "<warning>Priority: 0 (default)</warning>");
        }

        // Permissions
        Mint.sendMessage(player, "<warning>Permissions:</warning>");
        if (role.getPermissions() == 0) {
            Mint.sendMessage(player, "<neutral>  • None</neutral>");
        } else {
            for (GuildPermission perm : GuildPermission.values()) {
                if (role.hasPermission(perm)) {
                    Mint.sendMessage(player, "<success>  • " + perm.name() + "</success>");
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
        Mint.sendMessage(player, "<neutral></neutral>");

        // Members
        List<UUID> memberIds = memberRoleRepository.getMembersWithRole(role.getId());
        Mint.sendMessage(player, "<warning>Members (" + memberIds.size() + "):</warning>");

        if (memberIds.isEmpty()) {
            Mint.sendMessage(player, "<neutral>No members assigned</neutral>");
        } else {
            int displayLimit = 10;
            for (int i = 0; i < Math.min(displayLimit, memberIds.size()); i++) {
                UUID memberId = memberIds.get(i);
                var offlinePlayer = Bukkit.getOfflinePlayer(memberId);
                String memberName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
                if (offlinePlayer.isOnline()) {
                    Mint.sendMessage(player, "<success>  • " + memberName + "</success>");
                } else {
                    Mint.sendMessage(player, "<neutral>  • " + memberName + "</neutral>");
                }
            }
            if (memberIds.size() > displayLimit) {
                Mint.sendMessage(player, "<neutral>  ...and " + (memberIds.size() - displayLimit) + " more</neutral>");
            }
        }

        Mint.sendMessage(player, "<neutral></neutral>");

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

            Mint.sendMessage(player, "<neutral></neutral>");
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
