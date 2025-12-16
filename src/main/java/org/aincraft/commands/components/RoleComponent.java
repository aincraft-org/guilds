package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Component for managing guild roles.
 * Subcommands: create, delete, list, info, setperm, assign, unassign
 */
public final class RoleComponent implements GuildCommand {
    private final GuildService guildService;

    public RoleComponent(GuildService guildService) {
        this.guildService = guildService;
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
        return "/g role <create|delete|list|info|setperm|assign|unassign>";
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
            case "create" -> handleCreate(player, guild, args);
            case "delete" -> handleDelete(player, guild, args);
            case "list" -> handleList(player, guild);
            case "info" -> handleInfo(player, guild, args);
            case "setperm" -> handleSetPerm(player, guild, args);
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
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role create <name> [perms]", "Create role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role delete <name>", "Delete role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role list", "List roles"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role info <name>", "Show role info"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role setperm <name> <perm> <true|false>", "Set permission"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role assign <player> <role>", "Assign role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g role unassign <player> <role>", "Unassign role"));
    }

    private boolean handleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role create <name> [permissions]"));
            player.sendMessage(MessageFormatter.deserialize("<gray>Permissions: integer or comma-separated (BUILD,DESTROY,INTERACT,CLAIM,UNCLAIM,INVITE,KICK,MANAGE_ROLES)"));
            return true;
        }

        String name = args[2];
        int permissions = 0;

        if (args.length > 3) {
            permissions = parsePermissions(args[3]);
            if (permissions == -1) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid permissions format"));
                return true;
            }
        }

        GuildRole role = guildService.createRole(guild.getId(), player.getUniqueId(), name, permissions);
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to create role. Name may exist or you lack MANAGE_ROLES permission."));
            return true;
        }

        player.sendMessage(MessageFormatter.deserialize("<green>Created role '<gold>" + name + "</gold>'</green>"));
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

    private boolean handleList(Player player, Guild guild) {
        List<GuildRole> roles = guildService.getGuildRoles(guild.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Roles", ""));
        if (roles.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<gray>No roles defined"));
        } else {
            for (GuildRole role : roles) {
                player.sendMessage(MessageFormatter.deserialize("<yellow>" + role.getName() + "<gray> [" + role.getPermissions() + "]"));
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

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Role: " + role.getName(), ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Permissions", formatPermissions(role.getPermissions())));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Bitfield", String.valueOf(role.getPermissions())));
        return true;
    }

    private boolean handleSetPerm(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g role setperm <role> <permission> <true|false>"));
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
     * Parses permission string as either integer or comma-separated names.
     */
    private int parsePermissions(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            // Parse as comma-separated permission names
            int result = 0;
            for (String name : input.split(",")) {
                try {
                    GuildPermission perm = GuildPermission.valueOf(name.trim().toUpperCase());
                    result |= perm.getBit();
                } catch (IllegalArgumentException ex) {
                    return -1;
                }
            }
            return result;
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
