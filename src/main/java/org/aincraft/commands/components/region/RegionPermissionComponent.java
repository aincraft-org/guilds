package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.subregion.RegionPermission;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.subregion.RegionRole;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Handles region permissions and roles: setperm, removeperm, listperms, role + sub-commands.
 * Single Responsibility: Manages region-level permissions and role assignments.
 */
public class RegionPermissionComponent {
    private final SubregionService subregionService;
    private final RegionPermissionService permissionService;
    private final RegionCommandHelper helper;

    @Inject
    public RegionPermissionComponent(SubregionService subregionService, RegionPermissionService permissionService,
                                    RegionCommandHelper helper) {
        this.subregionService = subregionService;
        this.permissionService = permissionService;
        this.helper = helper;
    }

    /**
     * Sets permissions for a player or role in a region.
     *
     * @param player the player
     * @param args command args [2] = region, [3] = player|role, [4] = target, [5] = permissions
     * @return true if handled
     */
    public boolean handleSetPerm(Player player, String[] args) {
        if (args.length < 6) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region setperm <region> <player|role> <target> <permissions>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        String subjectType = args[3].toLowerCase();
        String targetIdentifier = args[4];
        String permString = args[5];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        // Parse permissions
        int permissions;
        try {
            permissions = Integer.parseInt(permString);
        } catch (NumberFormatException e) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)");
            return true;
        }

        switch (subjectType) {
            case "player" -> {
                Player target = helper.requirePlayer(targetIdentifier, player);
                if (target == null) {
                    return true;
                }
                permissionService.setPlayerPermission(region.getId(), target.getUniqueId(), permissions, player.getUniqueId());
                Messages.send(player, MessageKey.REGION_PERMISSION_ADDED, target.getName(), regionName);
            }
            case "role" -> {
                permissionService.setRolePermission(region.getId(), targetIdentifier, permissions, player.getUniqueId());
                Messages.send(player, MessageKey.REGION_PERMISSION_ADDED, targetIdentifier, regionName);
            }
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, "Subject type must be 'player' or 'role'");
                return true;
            }
        }

        return true;
    }

    /**
     * Removes permissions for a player or role in a region.
     *
     * @param player the player
     * @param args command args [2] = region, [3] = player|role, [4] = target
     * @return true if handled
     */
    public boolean handleRemovePerm(Player player, String[] args) {
        if (args.length < 5) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region removeperm <region> <player|role> <target>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        String subjectType = args[3].toLowerCase();
        String targetIdentifier = args[4];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        boolean removed;
        switch (subjectType) {
            case "player" -> {
                Player target = helper.requirePlayer(targetIdentifier, player);
                if (target == null) {
                    return true;
                }
                removed = permissionService.removePlayerPermission(region.getId(), target.getUniqueId());
                if (removed) {
                    Messages.send(player, MessageKey.REGION_PERMISSION_REMOVED, target.getName(), regionName);
                } else {
                    Messages.send(player, MessageKey.ERROR_USAGE, "No permissions found for that player");
                }
            }
            case "role" -> {
                removed = permissionService.removeRolePermission(region.getId(), targetIdentifier);
                if (removed) {
                    Messages.send(player, MessageKey.REGION_PERMISSION_REMOVED, targetIdentifier, regionName);
                } else {
                    Messages.send(player, MessageKey.ERROR_USAGE, "No permissions found for that role");
                }
            }
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, "Subject type must be 'player' or 'role'");
                return true;
            }
        }

        return true;
    }

    /**
     * Lists all permissions for a region.
     *
     * @param player the player
     * @param args command args [2] = region name
     * @return true if handled
     */
    public boolean handleListPerms(Player player, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region listperms <region>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        List<RegionPermission> permissions = permissionService.getRegionPermissions(region.getId());

        Messages.send(player, MessageKey.LIST_HEADER);

        if (region.getPermissions() != 0) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("Default: " + region.getPermissions()));
        }

        List<RegionPermission> playerPerms = permissionService.getPlayerPermissions(region.getId());
        if (!playerPerms.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<yellow>Player Permissions:</yellow>"));
            for (RegionPermission perm : playerPerms) {
                String playerName = Bukkit.getOfflinePlayer(UUID.fromString(perm.getSubjectId())).getName();
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<gray>  • <gold>" + (playerName != null ? playerName : perm.getSubjectId()) + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        List<RegionPermission> rolePerms = permissionService.getRolePermissions(region.getId());
        if (!rolePerms.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<yellow>Role Permissions:</yellow>"));
            for (RegionPermission perm : rolePerms) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<gray>  • <gold>" + perm.getSubjectId() + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        if (permissions.isEmpty() && region.getPermissions() == 0) {
            Messages.send(player, MessageKey.LIST_EMPTY);
        }

        return true;
    }

    /**
     * Routes role management sub-commands.
     *
     * @param player the player
     * @param guild the guild
     * @param args command args
     * @return true if handled
     */
    public boolean handleRole(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role <create|delete|list|assign|unassign|members> ...");
            return true;
        }

        String roleSubCommand = args[2].toLowerCase();

        return switch (roleSubCommand) {
            case "create" -> handleRoleCreate(player, guild, args);
            case "delete" -> handleRoleDelete(player, guild, args);
            case "list" -> handleRoleList(player, guild, args);
            case "assign" -> handleRoleAssign(player, guild, args);
            case "unassign" -> handleRoleUnassign(player, guild, args);
            case "members" -> handleRoleMembers(player, guild, args);
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, "Unknown role subcommand: " + roleSubCommand);
                yield true;
            }
        };
    }

    /**
     * Creates a new region role.
     */
    private boolean handleRoleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role create <region> <name> <permissions>");
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String permString = args[5];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        int permissions;
        try {
            permissions = Integer.parseInt(permString);
        } catch (NumberFormatException e) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)");
            return true;
        }

        RegionRole role = permissionService.createRegionRole(region.getId(), roleName, permissions, player.getUniqueId());
        if (role == null) {
            Messages.send(player, MessageKey.ERROR_USAGE, "A role with that name already exists in this region");
            return true;
        }

        Messages.send(player, MessageKey.ROLE_CREATED, roleName);
        return true;
    }

    /**
     * Deletes a region role.
     */
    private boolean handleRoleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role delete <region> <name>");
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        if (permissionService.deleteRegionRole(region.getId(), roleName)) {
            Messages.send(player, MessageKey.ROLE_DELETED, roleName);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Role not found: " + roleName);
        }

        return true;
    }

    /**
     * Lists region roles.
     */
    private boolean handleRoleList(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role list <region>");
            return true;
        }

        String regionName = args[3];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        List<RegionRole> roles = permissionService.getRegionRoles(region.getId());

        if (roles.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
            return true;
        }

        Messages.send(player, MessageKey.LIST_HEADER);
        for (RegionRole role : roles) {
            int memberCount = permissionService.getMembersWithRegionRole(region.getId(), role.getName()).size();
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<gray>• <gold>" + role.getName() + "</gold> - perms: " + role.getPermissions() + ", members: " + memberCount + "</gray>"));
        }

        return true;
    }

    /**
     * Assigns a player to a region role.
     */
    private boolean handleRoleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role assign <region> <role> <player>");
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String targetName = args[5];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        Player target = helper.requirePlayer(targetName, player);
        if (target == null) {
            return true;
        }

        if (permissionService.assignRegionRole(region.getId(), roleName, target.getUniqueId())) {
            Messages.send(player, MessageKey.ROLE_ASSIGNED, target.getName(), roleName);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Role not found: " + roleName);
        }

        return true;
    }

    /**
     * Unassigns a player from a region role.
     */
    private boolean handleRoleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role unassign <region> <role> <player>");
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String targetName = args[5];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        if (!helper.requireModifyPermission(region, player.getUniqueId(), player)) {
            return true;
        }

        Player target = helper.requirePlayer(targetName, player);
        if (target == null) {
            return true;
        }

        if (permissionService.unassignRegionRole(region.getId(), roleName, target.getUniqueId())) {
            Messages.send(player, MessageKey.ROLE_UNASSIGNED, target.getName(), roleName);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Role not found: " + roleName);
        }

        return true;
    }

    /**
     * Lists members of a region role.
     */
    private boolean handleRoleMembers(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region role members <region> <role>");
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        Optional<RegionRole> roleOpt = permissionService.getRegionRole(region.getId(), roleName);
        if (roleOpt.isEmpty()) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Role not found: " + roleName);
            return true;
        }

        List<UUID> members = permissionService.getMembersWithRegionRole(region.getId(), roleName);

        if (members.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
            return true;
        }

        Messages.send(player, MessageKey.LIST_HEADER);
        for (UUID memberId : members) {
            String memberName = Bukkit.getOfflinePlayer(memberId).getName();
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<gray>• <gold>" + (memberName != null ? memberName : memberId.toString()) + "</gold></gray>"));
        }

        return true;
    }
}
