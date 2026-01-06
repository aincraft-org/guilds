package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
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
            Mint.sendMessage(player, "<error>Usage: /g region setperm <region> <player|role> <target> <permissions></error>");
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
            Mint.sendMessage(player, String.format("<error>Invalid permission value. Use an integer (e.g., <accent>%d</accent> for BUILD)</error>", GuildPermission.BUILD.getBit()));
            return true;
        }

        switch (subjectType) {
            case "player" -> {
                Player target = helper.requirePlayer(targetIdentifier, player);
                if (target == null) {
                    return true;
                }
                permissionService.setPlayerPermission(region.getId(), target.getUniqueId(), permissions, player.getUniqueId());
                Mint.sendMessage(player, String.format("<success>Permissions set for <secondary>%s</secondary> in region <secondary>%s</secondary></success>", target.getName(), regionName));
            }
            case "role" -> {
                permissionService.setRolePermission(region.getId(), targetIdentifier, permissions, player.getUniqueId());
                Mint.sendMessage(player, String.format("<success>Permissions set for role <secondary>%s</secondary> in region <secondary>%s</secondary></success>", targetIdentifier, regionName));
            }
            default -> {
                Mint.sendMessage(player, "<error>Subject type must be 'player' or 'role'</error>");
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
            Mint.sendMessage(player, "<error>Usage: /g region removeperm <region> <player|role> <target></error>");
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
                    Mint.sendMessage(player, String.format("<success>Permissions removed for <secondary>%s</secondary> in region <secondary>%s</secondary></success>", target.getName(), regionName));
                } else {
                    Mint.sendMessage(player, "<error>No permissions found for that player</error>");
                }
            }
            case "role" -> {
                removed = permissionService.removeRolePermission(region.getId(), targetIdentifier);
                if (removed) {
                    Mint.sendMessage(player, String.format("<success>Permissions removed for role <secondary>%s</secondary> in region <secondary>%s</secondary></success>", targetIdentifier, regionName));
                } else {
                    Mint.sendMessage(player, "<error>No permissions found for that role</error>");
                }
            }
            default -> {
                Mint.sendMessage(player, "<error>Subject type must be 'player' or 'role'</error>");
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
            Mint.sendMessage(player, "<error>Usage: /g region listperms <region></error>");
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

        Mint.sendMessage(player, String.format("<primary>=== %s <accent>Permissions</accent> ===</primary>", regionName));

        if (region.getPermissions() != 0) {
            Mint.sendMessage(player, "Default: <accent>" + region.getPermissions() + "</accent>");
        }

        List<RegionPermission> playerPerms = permissionService.getPlayerPermissions(region.getId());
        if (!playerPerms.isEmpty()) {
            Mint.sendMessage(player, "<accent>Player Permissions:</accent>");
            for (RegionPermission perm : playerPerms) {
                String playerName = Bukkit.getOfflinePlayer(UUID.fromString(perm.getSubjectId())).getName();
                Mint.sendMessage(player,
                        "<neutral>  • <secondary>" + (playerName != null ? playerName : perm.getSubjectId()) + "</secondary>: <accent>" + perm.getPermissions() + "</accent></neutral>");
            }
        }

        List<RegionPermission> rolePerms = permissionService.getRolePermissions(region.getId());
        if (!rolePerms.isEmpty()) {
            Mint.sendMessage(player, "<accent>Role Permissions:</accent>");
            for (RegionPermission perm : rolePerms) {
                Mint.sendMessage(player,
                        "<neutral>  • <secondary>" + perm.getSubjectId() + "</secondary>: <accent>" + perm.getPermissions() + "</accent></neutral>");
            }
        }

        if (permissions.isEmpty() && region.getPermissions() == 0) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
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
            Mint.sendMessage(player, "<error>Usage: /g region role <create|delete|list|assign|unassign|members> ...</error>");
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
                Mint.sendMessage(player, String.format("<error>Unknown role subcommand: <accent>%s</accent></error>", roleSubCommand));
                yield true;
            }
        };
    }

    /**
     * Creates a new region role.
     */
    private boolean handleRoleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Mint.sendMessage(player, "<error>Usage: /g region role create <region> <name> <permissions></error>");
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
            Mint.sendMessage(player, String.format("<error>Invalid permission value. Use an integer (e.g., <accent>%d</accent> for BUILD)</error>", GuildPermission.BUILD.getBit()));
            return true;
        }

        RegionRole role = permissionService.createRegionRole(region.getId(), roleName, permissions, player.getUniqueId());
        if (role == null) {
            Mint.sendMessage(player, "<error>A role with that name already exists in this region</error>");
            return true;
        }

        Mint.sendMessage(player, String.format("<success>Role <secondary>%s</secondary> created!</success>", roleName));
        return true;
    }

    /**
     * Deletes a region role.
     */
    private boolean handleRoleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Mint.sendMessage(player, "<error>Usage: /g region role delete <region> <name></error>");
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
            Mint.sendMessage(player, String.format("<warning>Role <secondary>%s</secondary> deleted</warning>", roleName));
        } else {
            Mint.sendMessage(player, String.format("<error>Role not found: %s</error>", roleName));
        }

        return true;
    }

    /**
     * Lists region roles.
     */
    private boolean handleRoleList(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g region role list <region></error>");
            return true;
        }

        String regionName = args[3];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        List<RegionRole> roles = permissionService.getRegionRoles(region.getId());

        if (roles.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, String.format("<primary>=== %s Roles ===</primary>", regionName));
        for (RegionRole role : roles) {
            int memberCount = permissionService.getMembersWithRegionRole(region.getId(), role.getName()).size();
            Mint.sendMessage(player,
                    "<neutral>• <secondary>" + role.getName() + "</secondary> - perms: " + role.getPermissions() + ", members: " + memberCount + "</neutral>");
        }

        return true;
    }

    /**
     * Assigns a player to a region role.
     */
    private boolean handleRoleAssign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Mint.sendMessage(player, "<error>Usage: /g region role assign <region> <role> <player></error>");
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
            Mint.sendMessage(player, String.format("<success><secondary>%s</secondary> assigned to role <secondary>%s</secondary></success>", target.getName(), roleName));
        } else {
            Mint.sendMessage(player, String.format("<error>Role not found: %s</error>", roleName));
        }

        return true;
    }

    /**
     * Unassigns a player from a region role.
     */
    private boolean handleRoleUnassign(Player player, Guild guild, String[] args) {
        if (args.length < 6) {
            Mint.sendMessage(player, "<error>Usage: /g region role unassign <region> <role> <player></error>");
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
            Mint.sendMessage(player, String.format("<success><secondary>%s</secondary> unassigned from role <secondary>%s</secondary></success>", target.getName(), roleName));
        } else {
            Mint.sendMessage(player, String.format("<error>Role not found: %s</error>", roleName));
        }

        return true;
    }

    /**
     * Lists members of a region role.
     */
    private boolean handleRoleMembers(Player player, Guild guild, String[] args) {
        if (args.length < 5) {
            Mint.sendMessage(player, "<error>Usage: /g region role members <region> <role></error>");
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
            Mint.sendMessage(player, String.format("<error>Role not found: %s</error>", roleName));
            return true;
        }

        List<UUID> members = permissionService.getMembersWithRegionRole(region.getId(), roleName);

        if (members.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, String.format("<primary>=== %s Role Members ===</primary>", roleName));
        for (UUID memberId : members) {
            String memberName = Bukkit.getOfflinePlayer(memberId).getName();
            Mint.sendMessage(player,
                    "<neutral>• <secondary>" + (memberName != null ? memberName : memberId.toString()) + "</secondary></neutral>");
        }

        return true;
    }
}
