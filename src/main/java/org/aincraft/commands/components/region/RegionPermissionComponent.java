package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.MessageFormatter;
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region setperm <region> <player|role> <target> <permissions>"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)"));
            return true;
        }

        switch (subjectType) {
            case "player" -> {
                Player target = helper.requirePlayer(targetIdentifier, player);
                if (target == null) {
                    return true;
                }
                permissionService.setPlayerPermission(region.getId(), target.getUniqueId(), permissions, player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Set permissions for <gold>" + target.getName() + "</gold> in region <gold>" + regionName + "</gold> to <yellow>" + permissions + "</yellow></green>"));
            }
            case "role" -> {
                permissionService.setRolePermission(region.getId(), targetIdentifier, permissions, player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Set permissions for role <gold>" + targetIdentifier + "</gold> in region <gold>" + regionName + "</gold> to <yellow>" + permissions + "</yellow></green>"));
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Subject type must be 'player' or 'role'"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region removeperm <region> <player|role> <target>"));
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
                    player.sendMessage(MessageFormatter.deserialize(
                            "<green>Removed permissions for <gold>" + target.getName() + "</gold> from region <gold>" + regionName + "</gold></green>"));
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No permissions found for that player"));
                }
            }
            case "role" -> {
                removed = permissionService.removeRolePermission(region.getId(), targetIdentifier);
                if (removed) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<green>Removed permissions for role <gold>" + targetIdentifier + "</gold> from region <gold>" + regionName + "</gold></green>"));
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No permissions found for that role"));
                }
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Subject type must be 'player' or 'role'"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region listperms <region>"));
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

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Permissions for " + regionName, ""));

        if (region.getPermissions() != 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Default", String.valueOf(region.getPermissions())));
        }

        List<RegionPermission> playerPerms = permissionService.getPlayerPermissions(region.getId());
        if (!playerPerms.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Player Permissions:</yellow>"));
            for (RegionPermission perm : playerPerms) {
                String playerName = Bukkit.getOfflinePlayer(UUID.fromString(perm.getSubjectId())).getName();
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>  • <gold>" + (playerName != null ? playerName : perm.getSubjectId()) + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        List<RegionPermission> rolePerms = permissionService.getRolePermissions(region.getId());
        if (!rolePerms.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Role Permissions:</yellow>"));
            for (RegionPermission perm : rolePerms) {
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>  • <gold>" + perm.getSubjectId() + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        if (permissions.isEmpty() && region.getPermissions() == 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No custom permissions set"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region role <create|delete|list|assign|unassign|members> ..."));
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
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown role subcommand: " + roleSubCommand));
                yield true;
            }
        };
    }

    /**
     * Creates a new region role.
     */
    private boolean handleRoleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region role create <region> <name> <permissions>"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)"));
            return true;
        }

        RegionRole role = permissionService.createRegionRole(region.getId(), roleName, permissions, player.getUniqueId());
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "A role with that name already exists in this region"));
            return true;
        }

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Created region role <gold>" + roleName + "</gold> with permissions <yellow>" + permissions + "</yellow></green>"));
        return true;
    }

    /**
     * Deletes a region role.
     */
    private boolean handleRoleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role delete <region> <name>"));
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
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Deleted region role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    /**
     * Lists region roles.
     */
    private boolean handleRoleList(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role list <region>"));
            return true;
        }

        String regionName = args[3];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        List<RegionRole> roles = permissionService.getRegionRoles(region.getId());

        if (roles.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No roles defined for this region"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Roles for " + regionName, " (" + roles.size() + ")"));
        for (RegionRole role : roles) {
            int memberCount = permissionService.getMembersWithRegionRole(region.getId(), role.getName()).size();
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + role.getName() + "</gold> - perms: " + role.getPermissions() + ", members: " + memberCount + "</gray>"));
        }

        return true;
    }

    /**
     * Assigns a player to a region role.
     */
    private boolean handleRoleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region role assign <region> <role> <player>"));
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
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Assigned <gold>" + target.getName() + "</gold> to role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    /**
     * Unassigns a player from a region role.
     */
    private boolean handleRoleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Usage: /g region role unassign <region> <role> <player>"));
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
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Unassigned <gold>" + target.getName() + "</gold> from role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    /**
     * Lists members of a region role.
     */
    private boolean handleRoleMembers(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role members <region> <role>"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        List<UUID> members = permissionService.getMembersWithRegionRole(region.getId(), roleName);

        if (members.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No members assigned to role: " + roleName));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Members with " + roleName, " (" + members.size() + ")"));
        for (UUID memberId : members) {
            String memberName = Bukkit.getOfflinePlayer(memberId).getName();
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + (memberName != null ? memberName : memberId.toString()) + "</gold></gray>"));
        }

        return true;
    }
}
