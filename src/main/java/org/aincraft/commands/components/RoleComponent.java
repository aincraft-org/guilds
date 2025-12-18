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
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.role.gui.RoleCreationGUI;
import org.aincraft.storage.MemberRoleRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for managing guild roles.
 * Commands:
 * - `/g role editor <name>` - Open role editor GUI (creates if doesn't exist, edits if exists)
 * - `/g role create <name> <perms> <priority>` - Create role directly (verbose)
 * - `/g role delete <name>` - Delete role
 * - `/g role perm <name> <perm> <true|false>` - Set permission
 * - `/g role priority <name> <priority>` - Set role priority
 * - `/g role list` - List all roles
 * - `/g role info <name>` - Show role information
 * - `/g role assign <player> <role>` - Assign role to player
 * - `/g role unassign <player> <role>` - Unassign role from player
 */
public final class RoleComponent implements GuildCommand {
    private final GuildService guildService;
    private final MemberRoleRepository memberRoleRepository;

    @Inject
    public RoleComponent(GuildService guildService, MemberRoleRepository memberRoleRepository) {
        this.guildService = guildService;
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
        return "/g role <editor|create|delete|perm|priority|list|info|assign|unassign>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to manage roles"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
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
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Role Commands", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role editor <name>", "Open role editor (create or edit)"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role create <name> <perms> <priority>", "Create role directly"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role delete <name>", "Delete role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role perm <name> <perm> <true|false>", "Set permission"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role priority <name> <priority>", "Set role priority"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role list", "List roles"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role info <name>", "Show role info"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role assign <player> <role>", "Assign role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role unassign <player> <role>", "Unassign role"));
    }

    /**
     * Handles role editor - opens GUI for creating new role or editing existing one.
     */
    private boolean handleEditor(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role editor <name>"));
            return true;
        }

        String name = args[2];

        // Check MANAGE_ROLES permission
        if (!guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You lack MANAGE_ROLES permission."));
            return true;
        }

        // Open editor GUI (will create if doesn't exist, edit if exists)
        RoleCreationGUI gui = new RoleCreationGUI(guild, player, name, guildService);
        gui.open();
        return true;
    }

    /**
     * Handles direct role creation with all parameters.
     */
    private boolean handleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role create <name> <perms> <priority>"));
            return true;
        }

        String name = args[2];

        // Check if role name already exists
        if (guildService.getRoleByName(guild.getId(), name) != null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "A role with that name already exists."));
            return true;
        }

        // Check MANAGE_ROLES permission
        if (!guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You lack MANAGE_ROLES permission."));
            return true;
        }

        int permissions = parsePermissions(args[3]);
        if (permissions == -1) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid permissions. Must be an integer."));
            return true;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid priority. Must be an integer."));
            return true;
        }

        GuildRole role = guildService.createRole(guild.getId(), player.getUniqueId(), name, permissions, priority);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to create role. You may lack MANAGE_ROLES permission."));
            return true;
        }

        String priorityText = priority > 0 ? " <gray>(priority: <yellow>" + priority + "</yellow>)</gray>" : "";
        player.sendMessage(MessageFormatter.deserialize("<green>Created role '<gold>" + name + "</gold>'" + priorityText + "</green>"));
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<GuildRole> roles = guildService.getGuildRoles(guild.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Roles", " (sorted by priority)"));
        if (roles.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<gray>No roles defined"));
        } else {
            for (GuildRole role : roles) {
                String priorityBadge = role.getPriority() > 0 ? "<dark_gray>[<gold>" + role.getPriority() + "</gold>]</dark_gray> " : "";
                player.sendMessage(MessageFormatter.deserialize(priorityBadge + "<yellow>" + role.getName() + "<gray> [" + role.getPermissions() + "]"));
            }
        }
        return true;
    }

    private boolean handleInfo(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role info <name>"));
            return true;
        }

        String name = args[2];
        GuildRole role = guildService.getRoleByName(guild.getId(), name);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + name));
            return true;
        }

        displayEnhancedRoleInfo(player, guild, role);
        return true;
    }

    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role delete <name>"));
            return true;
        }

        String name = args[2];
        GuildRole role = guildService.getRoleByName(guild.getId(), name);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + name));
            return true;
        }

        if (guildService.deleteRole(guild.getId(), player.getUniqueId(), role.getId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>Deleted role '<gold>" + name + "</gold>'</green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to delete role. You may lack MANAGE_ROLES permission."));
        }
        return true;
    }

    private boolean handleSetPerm(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role perm <role> <permission> <true|false>"));
            player.sendMessage(MessageFormatter.deserialize("<gray>Permissions: BUILD, DESTROY, INTERACT, CLAIM, UNCLAIM, INVITE, KICK, MANAGE_ROLES"));
            return true;
        }

        String roleName = args[2];
        String permName = args[3].toUpperCase();
        boolean grant = args[4].equalsIgnoreCase("true");

        GuildRole role = guildService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        GuildPermission perm;
        try {
            perm = GuildPermission.valueOf(permName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown permission: " + permName));
            return true;
        }

        int newPermissions = role.getPermissions();
        if (grant) {
            newPermissions |= perm.getBit();
        } else {
            newPermissions &= ~perm.getBit();
        }

        if (guildService.updateRolePermissions(guild.getId(), player.getUniqueId(), role.getId(), newPermissions)) {
            String action = grant ? "granted" : "revoked";
            player.sendMessage(MessageFormatter.deserialize("<green>" + action.substring(0, 1).toUpperCase() + action.substring(1) + " " +
                    "<gold>" + permName + "</gold> for role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to update permissions. You may lack MANAGE_ROLES permission."));
        }
        return true;
    }

    private boolean handleSetPriority(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role priority <role> <priority>"));
            player.sendMessage(MessageFormatter.deserialize("<gray>Priority: higher number = more authority"));
            return true;
        }

        String roleName = args[2];
        int priority;

        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid priority. Must be an integer."));
            return true;
        }

        GuildRole role = guildService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        // Check permission
        if (!guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_ROLES)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You lack MANAGE_ROLES permission."));
            return true;
        }

        role.setPriority(priority);
        guildService.saveRole(role);

        player.sendMessage(MessageFormatter.deserialize("<green>Set priority of <gold>" + roleName + "</gold> to <yellow>" + priority + "</yellow></green>"));
        return true;
    }

    private boolean handleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role assign <player> <role>"));
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found: " + targetName));
            return true;
        }

        GuildRole role = guildService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        if (guildService.assignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>Assigned <gold>" + roleName +
                    "</gold> to <gold>" + target.getName() + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to assign role. Player may not be a member or you lack permission."));
        }
        return true;
    }

    private boolean handleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role unassign <player> <role>"));
            return true;
        }

        String targetName = args[2];
        String roleName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found: " + targetName));
            return true;
        }

        GuildRole role = guildService.getRoleByName(guild.getId(), roleName);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        if (guildService.unassignRole(guild.getId(), player.getUniqueId(), target.getUniqueId(), role.getId())) {
            player.sendMessage(MessageFormatter.deserialize("<green>Unassigned <gold>" + roleName +
                    "</gold> from <gold>" + target.getName() + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to unassign role. You may lack MANAGE_ROLES permission."));
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

        player.sendMessage(MessageFormatter.deserialize(ROLE_HEADER,
                Placeholder.component("role_name", Component.text(roleName)),
                Placeholder.component("left_underline", Component.text(underline)),
                Placeholder.component("right_underline", Component.text(underline))));
        player.sendMessage(Component.empty());

        // Priority
        if (role.getPriority() > 0) {
            player.sendMessage(MessageFormatter.deserialize(ROLE_PRIORITY_WITH_VALUE,
                    Placeholder.component("priority", Component.text(role.getPriority()))));
        } else {
            player.sendMessage(MessageFormatter.deserialize(ROLE_PRIORITY_DEFAULT));
        }

        // Permissions
        player.sendMessage(MessageFormatter.deserialize(ROLE_PERMISSIONS_HEADER));
        if (role.getPermissions() == 0) {
            player.sendMessage(MessageFormatter.deserialize(ROLE_PERMISSIONS_NONE));
        } else {
            for (GuildPermission perm : GuildPermission.values()) {
                if (role.hasPermission(perm)) {
                    player.sendMessage(MessageFormatter.deserialize(ROLE_PERMISSION_ITEM,
                            Placeholder.component("permission", Component.text(perm.name()))));
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
        player.sendMessage(MessageFormatter.deserialize(ROLE_MEMBERS_HEADER,
                Placeholder.component("member_count", Component.text(memberIds.size()))));

        if (memberIds.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize(ROLE_MEMBERS_NONE));
        } else {
            int displayLimit = 10;
            for (int i = 0; i < Math.min(displayLimit, memberIds.size()); i++) {
                UUID memberId = memberIds.get(i);
                var offlinePlayer = Bukkit.getOfflinePlayer(memberId);
                String memberName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
                String statusColor = offlinePlayer.isOnline() ? "green" : "gray";

                player.sendMessage(MessageFormatter.deserialize(ROLE_MEMBER_ITEM,
                        Placeholder.component("member_name", Component.text(memberName)),
                        Placeholder.component("status_color", Component.text(statusColor))));
            }
            if (memberIds.size() > displayLimit) {
                player.sendMessage(MessageFormatter.deserialize(ROLE_MEMBERS_MORE,
                        Placeholder.component("more_count", Component.text(memberIds.size() - displayLimit))));
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
