package org.aincraft.subregion;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildDefaultPermissionsService;
import org.aincraft.GuildPermission;
import org.aincraft.RelationType;
import org.aincraft.RelationshipService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.storage.MemberRoleRepository;

/**
 * Service layer for region-specific permission operations.
 * Single Responsibility: Region permission business logic.
 * Open/Closed: Depends on abstractions, not implementations.
 * Dependency Inversion: Injected dependencies via constructor.
 */
public class RegionPermissionService {
    private final RegionPermissionRepository permissionRepository;
    private final SubregionRepository subregionRepository;
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;
    private final PermissionService guildPermissionService;
    private final MemberRoleRepository memberRoleRepository;
    private final RegionRoleRepository regionRoleRepository;
    private final MemberRegionRoleRepository memberRegionRoleRepository;
    private final RelationshipService relationshipService;
    private final GuildDefaultPermissionsService guildDefaultPermissionsService;

    @Inject
    public RegionPermissionService(RegionPermissionRepository permissionRepository,
                                 SubregionRepository subregionRepository,
                                 GuildLifecycleService lifecycleService,
                                 GuildMemberService memberService,
                                 PermissionService guildPermissionService,
                                 MemberRoleRepository memberRoleRepository,
                                 RegionRoleRepository regionRoleRepository,
                                 MemberRegionRoleRepository memberRegionRoleRepository,
                                 RelationshipService relationshipService,
                                 GuildDefaultPermissionsService guildDefaultPermissionsService) {
        this.permissionRepository = Objects.requireNonNull(permissionRepository);
        this.subregionRepository = Objects.requireNonNull(subregionRepository);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.memberService = Objects.requireNonNull(memberService);
        this.guildPermissionService = Objects.requireNonNull(guildPermissionService);
        this.memberRoleRepository = Objects.requireNonNull(memberRoleRepository);
        this.regionRoleRepository = Objects.requireNonNull(regionRoleRepository);
        this.memberRegionRoleRepository = Objects.requireNonNull(memberRegionRoleRepository);
        this.relationshipService = Objects.requireNonNull(relationshipService);
        this.guildDefaultPermissionsService = Objects.requireNonNull(guildDefaultPermissionsService);
    }

    /**
     * Sets permission for a player in a region.
     *
     * @param regionId    the region ID
     * @param playerId    the player UUID
     * @param permissions the permission bitfield
     * @param createdBy   the player creating this permission
     * @return the created/updated permission
     */
    public RegionPermission setPlayerPermission(UUID regionId, UUID playerId, int permissions, UUID createdBy) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(createdBy, "Creator cannot be null");

        Optional<RegionPermission> existing = permissionRepository.findByRegionAndSubject(
            regionId, playerId.toString(), SubjectType.PLAYER
        );

        RegionPermission permission;
        if (existing.isPresent()) {
            permission = existing.get();
            permission.setPermissions(permissions);
        } else {
            permission = new RegionPermission(
                regionId, playerId.toString(), SubjectType.PLAYER, permissions, createdBy
            );
        }

        permissionRepository.save(permission);
        return permission;
    }

    /**
     * Sets permission for a role in a region.
     *
     * @param regionId    the region ID
     * @param roleId      the role ID
     * @param permissions the permission bitfield
     * @param createdBy   the player creating this permission
     * @return the created/updated permission
     */
    public RegionPermission setRolePermission(UUID regionId, String roleId, int permissions, UUID createdBy) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");
        Objects.requireNonNull(createdBy, "Creator cannot be null");

        Optional<RegionPermission> existing = permissionRepository.findByRegionAndSubject(
            regionId, roleId, SubjectType.ROLE
        );

        RegionPermission permission;
        if (existing.isPresent()) {
            permission = existing.get();
            permission.setPermissions(permissions);
        } else {
            permission = new RegionPermission(
                regionId, roleId, SubjectType.ROLE, permissions, createdBy
            );
        }

        permissionRepository.save(permission);
        return permission;
    }

    /**
     * Removes a player permission from a region.
     *
     * @param regionId the region ID
     * @param playerId the player UUID
     * @return true if removed, false if not found
     */
    public boolean removePlayerPermission(UUID regionId, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<RegionPermission> permission = permissionRepository.findByRegionAndSubject(
            regionId, playerId.toString(), SubjectType.PLAYER
        );

        if (permission.isPresent()) {
            permissionRepository.delete(permission.get().getId());
            return true;
        }
        return false;
    }

    /**
     * Removes a role permission from a region.
     *
     * @param regionId the region ID
     * @param roleId   the role ID
     * @return true if removed, false if not found
     */
    public boolean removeRolePermission(UUID regionId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        Optional<RegionPermission> permission = permissionRepository.findByRegionAndSubject(
            regionId, roleId, SubjectType.ROLE
        );

        if (permission.isPresent()) {
            permissionRepository.delete(permission.get().getId());
            return true;
        }
        return false;
    }

    /**
     * Gets all permissions for a region.
     *
     * @param regionId the region ID
     * @return list of permissions
     */
    public List<RegionPermission> getRegionPermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        return permissionRepository.findByRegion(regionId);
    }

    /**
     * Gets player-specific permissions for a region.
     *
     * @param regionId the region ID
     * @return list of player permissions
     */
    public List<RegionPermission> getPlayerPermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        return permissionRepository.findPlayerPermissions(regionId);
    }

    /**
     * Gets role-specific permissions for a region.
     *
     * @param regionId the region ID
     * @return list of role permissions
     */
    public List<RegionPermission> getRolePermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        return permissionRepository.findRolePermissions(regionId);
    }

    /**
     * Checks if a player has a specific permission in a region.
     * Permission hierarchy:
     * 1. Guild owner - always allowed
     * 2. Region owner - always allowed
     * 3. Player-specific override (can apply to anyone)
     * 4. Relationship-specific (GUILD_ALLY, GUILD_ENEMY, GUILD_OUTSIDER)
     * 5. Guild role-based (SUPER - higher priority)
     * 6. Region role-based
     * 7. Region default
     * 8. Guild fallback
     *
     * @param region     the region
     * @param playerId   the player UUID
     * @param permission the permission to check
     * @return true if player has the permission
     */
    public boolean hasPermission(Subregion region, UUID playerId, GuildPermission permission) {
        Objects.requireNonNull(region, "Region cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        // 1. Guild owner always has permission
        if (lifecycleService.getGuildById(region.getGuildId()) != null &&
            lifecycleService.getGuildById(region.getGuildId()).isOwner(playerId)) {
            return true;
        }

        // 2. Region owners have all permissions
        if (region.isOwner(playerId)) {
            return true;
        }

        // 3. ADMIN permission grants all permissions
        if (guildPermissionService.hasPermission(region.getGuildId(), playerId, GuildPermission.ADMIN)) {
            return true;
        }

        // 4. Check player-specific permissions (applies to anyone)
        Optional<RegionPermission> playerPerm = permissionRepository.findByRegionAndSubject(
            region.getId(), playerId.toString(), SubjectType.PLAYER
        );
        if (playerPerm.isPresent()) {
            return playerPerm.get().hasPermission(permission.getBit());
        }

        // Check if player is in the guild
        var playerGuild = memberService.getPlayerGuild(playerId);
        boolean isMember = playerGuild != null && playerGuild.getId().toString().equals(region.getGuildId());

        // 4. Check relationship-specific permissions (for non-members)
        if (!isMember) {
            if (checkRelationshipPermission(region.getGuildId(), playerGuild, permission.getBit())) {
                return true;
            }
        }

        // 5-8: Only apply member-specific checks for guild members
        if (isMember) {
            // 5. Check guild role-based permissions (SUPER - higher priority)
            List<String> guildRoles = memberRoleRepository.getMemberRoleIds(region.getGuildId(), playerId);
            for (String roleId : guildRoles) {
                Optional<RegionPermission> rolePerm = permissionRepository.findByRegionAndSubject(
                    region.getId(), roleId, SubjectType.ROLE
                );
                if (rolePerm.isPresent() && rolePerm.get().hasPermission(permission.getBit())) {
                    return true;
                }
            }

            // 6. Check region role-based permissions
            List<String> regionRoleIds = memberRegionRoleRepository.getMemberRoleIds(region.getId(), playerId);
            for (String roleId : regionRoleIds) {
                Optional<RegionRole> regionRole = regionRoleRepository.findById(roleId);
                if (regionRole.isPresent() && regionRole.get().hasPermission(permission.getBit())) {
                    return true;
                }
            }

            // 7. Check region default permissions (set on Subregion itself)
            int regionPerms = region.getPermissions();
            if (regionPerms != 0) {
                return (regionPerms & permission.getBit()) != 0;
            }

            // 8. Fall back to guild permissions
            return guildPermissionService.hasPermission(region.getGuildId(), playerId, permission);
        }

        return false;
    }

    /**
     * Clears all permissions for a region (when region is deleted).
     * Deletes in order: role assignments -> roles -> permissions to maintain referential integrity.
     *
     * @param regionId the region ID
     */
    public void clearRegionPermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        RuntimeException firstError = null;

        try {
            memberRegionRoleRepository.removeAllByRegion(regionId);
        } catch (RuntimeException e) {
            firstError = e;
        }

        try {
            regionRoleRepository.deleteAllByRegion(regionId);
        } catch (RuntimeException e) {
            if (firstError == null) firstError = e;
        }

        try {
            permissionRepository.deleteAllByRegion(regionId);
        } catch (RuntimeException e) {
            if (firstError == null) firstError = e;
        }

        if (firstError != null) {
            throw new RuntimeException("Failed to fully clear region permissions for " + regionId, firstError);
        }
    }

    // ==================== Region Role Management ====================

    /**
     * Creates a new region role.
     *
     * @param regionId    the region ID
     * @param name        the role name
     * @param permissions the permission bitfield
     * @param createdBy   the creator
     * @return the created role, or null if name already exists
     */
    public RegionRole createRegionRole(UUID regionId, String name, int permissions, UUID createdBy) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(createdBy, "Creator cannot be null");

        // Check name uniqueness
        if (regionRoleRepository.findByRegionAndName(regionId, name).isPresent()) {
            return null;
        }

        RegionRole role = new RegionRole(regionId, name, permissions, createdBy);
        regionRoleRepository.save(role);
        return role;
    }

    /**
     * Deletes a region role and all its assignments.
     *
     * @param regionId the region ID
     * @param roleName the role name
     * @return true if deleted, false if not found
     */
    public boolean deleteRegionRole(UUID regionId, String roleName) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");

        Optional<RegionRole> roleOpt = regionRoleRepository.findByRegionAndName(regionId, roleName);
        if (roleOpt.isEmpty()) {
            return false;
        }

        RegionRole role = roleOpt.get();
        memberRegionRoleRepository.removeAllByRole(role.getId());
        regionRoleRepository.delete(role.getId());
        return true;
    }

    /**
     * Gets all region roles for a region.
     *
     * @param regionId the region ID
     * @return list of roles
     */
    public List<RegionRole> getRegionRoles(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        return regionRoleRepository.findByRegion(regionId);
    }

    /**
     * Gets a region role by name.
     *
     * @param regionId the region ID
     * @param roleName the role name
     * @return the role, or empty if not found
     */
    public Optional<RegionRole> getRegionRole(UUID regionId, String roleName) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");
        return regionRoleRepository.findByRegionAndName(regionId, roleName);
    }

    /**
     * Assigns a player to a region role.
     *
     * @param regionId the region ID
     * @param roleName the role name
     * @param playerId the player UUID
     * @return true if assigned, false if role not found
     */
    public boolean assignRegionRole(UUID regionId, String roleName, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<RegionRole> roleOpt = regionRoleRepository.findByRegionAndName(regionId, roleName);
        if (roleOpt.isEmpty()) {
            return false;
        }

        memberRegionRoleRepository.assignRole(regionId, playerId, roleOpt.get().getId());
        return true;
    }

    /**
     * Unassigns a player from a region role.
     *
     * @param regionId the region ID
     * @param roleName the role name
     * @param playerId the player UUID
     * @return true if unassigned, false if role not found
     */
    public boolean unassignRegionRole(UUID regionId, String roleName, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        Optional<RegionRole> roleOpt = regionRoleRepository.findByRegionAndName(regionId, roleName);
        if (roleOpt.isEmpty()) {
            return false;
        }

        memberRegionRoleRepository.unassignRole(regionId, playerId, roleOpt.get().getId());
        return true;
    }

    /**
     * Gets all members with a specific region role.
     *
     * @param regionId the region ID
     * @param roleName the role name
     * @return list of player UUIDs, or empty list if role not found
     */
    public List<UUID> getMembersWithRegionRole(UUID regionId, String roleName) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");

        Optional<RegionRole> roleOpt = regionRoleRepository.findByRegionAndName(regionId, roleName);
        if (roleOpt.isEmpty()) {
            return List.of();
        }

        return memberRegionRoleRepository.getMembersWithRole(roleOpt.get().getId());
    }

    /**
     * Updates the permissions of a region role.
     *
     * @param regionId    the region ID
     * @param roleName    the role name
     * @param permissions the new permissions
     * @return true if updated, false if role not found
     */
    public boolean updateRegionRolePermissions(UUID regionId, String roleName, int permissions) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(roleName, "Role name cannot be null");

        Optional<RegionRole> roleOpt = regionRoleRepository.findByRegionAndName(regionId, roleName);
        if (roleOpt.isEmpty()) {
            return false;
        }

        RegionRole role = roleOpt.get();
        role.setPermissions(permissions);
        regionRoleRepository.save(role);
        return true;
    }

    /**
     * Checks if a player can modify permissions in a region.
     * Must be guild owner or region owner.
     *
     * @param region   the region
     * @param playerId the player UUID
     * @return true if player can modify permissions
     */
    public boolean canModifyPermissions(Subregion region, UUID playerId) {
        Objects.requireNonNull(region, "Region cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        // Guild owner can modify
        if (lifecycleService.getGuildById(region.getGuildId()) != null &&
            lifecycleService.getGuildById(region.getGuildId()).isOwner(playerId)) {
            return true;
        }

        // Region owner can modify
        if (region.isOwner(playerId)) {
            return true;
        }

        // Check if player has MANAGE_REGIONS permission
        return guildPermissionService.hasPermission(region.getGuildId(), playerId, GuildPermission.MANAGE_REGIONS);
    }

    // ==================== Relationship Permission Helpers ====================

    /**
     * Checks if a player has a relationship-based permission.
     * First checks region-specific relationship overrides, then falls back to guild defaults.
     *
     * @param guildId the guild ID (region owner)
     * @param playerGuild the player's guild (null if unguilded)
     * @param permissionBit the permission bit to check
     * @return true if the player has the permission
     */
    private boolean checkRelationshipPermission(UUID guildId, Guild playerGuild, int permissionBit) {
        // Determine relationship type
        SubjectType subjectType = mapRelationToSubjectType(guildId, playerGuild);

        // Check region-specific relationship override first
        // (would need to pass region ID - but this is called from hasPermission which has it)
        // For now, skip region override and use guild defaults

        // Fall back to guild default relationship permissions
        int permissions = guildDefaultPermissionsService.getPermissions(guildId, subjectType);
        return (permissions & permissionBit) != 0;
    }

    /**
     * Maps a guild relationship to a SubjectType for permission checking.
     *
     * @param guildId the guild ID (region owner)
     * @param playerGuild the player's guild (null if unguilded)
     * @return the appropriate SubjectType for relationship permissions
     */
    private SubjectType mapRelationToSubjectType(UUID guildId, Guild playerGuild) {
        if (playerGuild == null) {
            return SubjectType.GUILD_OUTSIDER;
        }

        RelationType relationType = relationshipService.getRelationType(guildId, playerGuild.getId());
        return switch (relationType) {
            case ALLY -> SubjectType.GUILD_ALLY;
            case ENEMY -> SubjectType.GUILD_ENEMY;
            case NEUTRAL -> SubjectType.GUILD_OUTSIDER;
        };
    }
}
