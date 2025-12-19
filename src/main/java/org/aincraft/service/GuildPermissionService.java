package org.aincraft.service;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.MemberPermissions;
import org.aincraft.storage.GuildMemberRepository;
import org.aincraft.storage.GuildRepository;
import org.aincraft.storage.GuildRoleRepository;
import org.aincraft.storage.MemberRoleRepository;

/**
 * Service for checking and managing permissions.
 * Single Responsibility: Permission checking operations.
 */
public class GuildPermissionService {
    private final GuildRepository guildRepository;
    private final GuildMemberRepository memberRepository;
    private final GuildRoleRepository roleRepository;
    private final MemberRoleRepository memberRoleRepository;

    @Inject
    public GuildPermissionService(GuildRepository guildRepository,
                                  GuildMemberRepository memberRepository,
                                  GuildRoleRepository roleRepository,
                                  MemberRoleRepository memberRoleRepository) {
        this.guildRepository = Objects.requireNonNull(guildRepository, "GuildRepository cannot be null");
        this.memberRepository = Objects.requireNonNull(memberRepository, "MemberRepository cannot be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository cannot be null");
        this.memberRoleRepository = Objects.requireNonNull(memberRoleRepository, "MemberRoleRepository cannot be null");
    }

    /**
     * Checks if a player has a specific permission in a guild.
     * Permissions are computed by OR-ing all assigned role permissions.
     * Guild owners always have all permissions.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    public boolean hasPermission(UUID guildId, UUID playerId, GuildPermission permission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.isOwner(playerId)) {
            return true;
        }

        int effectivePermissions = computeEffectivePermissions(guildId, playerId);

        // ADMIN permission grants all permissions
        if ((effectivePermissions & GuildPermission.ADMIN.getBit()) != 0) {
            return true;
        }

        return (effectivePermissions & permission.getBit()) != 0;
    }

    /**
     * Gets the permissions for a member in a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the member's permissions, or null if not found
     */
    public MemberPermissions getMemberPermissions(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        return memberRepository.getPermissions(guildId, playerId).orElse(null);
    }

    /**
     * Sets the permissions for a member in a guild.
     * Only the guild owner can modify permissions.
     *
     * @param guildId the guild ID
     * @param requesterId the player requesting the change (must be owner)
     * @param targetId the player whose permissions to change
     * @param permissions the new permissions
     * @return true if permissions were updated
     */
    public boolean setMemberPermissions(UUID guildId, UUID requesterId, UUID targetId, MemberPermissions permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        if (!guild.isOwner(requesterId)) {
            return false;
        }

        if (!guild.isMember(targetId)) {
            return false;
        }

        memberRepository.setPermissions(guildId, targetId, permissions);
        return true;
    }

    /**
     * Checks if a kicker can kick a target based on role hierarchy.
     *
     * @param guild the guild
     * @param kickerId the UUID of the player attempting to kick
     * @param targetId the UUID of the player to be kicked
     * @return true if kicker can kick target based on hierarchy
     */
    public boolean canKickByHierarchy(Guild guild, UUID kickerId, UUID targetId) {
        // Cannot kick yourself
        if (kickerId.equals(targetId)) {
            return false;
        }

        // Owner can kick anyone
        if (guild.isOwner(kickerId)) {
            return true;
        }

        // Cannot kick owner
        if (guild.isOwner(targetId)) {
            return false;
        }

        // Check if target has ADMIN permission - only owner can kick admins
        boolean targetHasAdminPerm = hasPermission(guild.getId(), targetId, GuildPermission.ADMIN);
        if (targetHasAdminPerm) {
            return false;
        }

        // Check if kicker has ADMIN or KICK permission
        boolean hasAdminPerm = hasPermission(guild.getId(), kickerId, GuildPermission.ADMIN);
        boolean hasKickPerm = hasPermission(guild.getId(), kickerId, GuildPermission.KICK);

        if (!hasAdminPerm && !hasKickPerm) {
            return false;
        }

        // Admin can kick anyone (except owner and other admins, already checked)
        if (hasAdminPerm) {
            return true;
        }

        // For KICK permission, check role hierarchy
        Optional<GuildRole> kickerRoleOpt = getHighestPriorityRole(guild.getId(), kickerId);
        Optional<GuildRole> targetRoleOpt = getHighestPriorityRole(guild.getId(), targetId);

        // If target has no role, kicker can kick
        if (targetRoleOpt.isEmpty()) {
            return true;
        }

        // If kicker has no role but target does, cannot kick
        if (kickerRoleOpt.isEmpty()) {
            return false;
        }

        // Compare priorities: kicker must have higher priority
        return kickerRoleOpt.get().hasHigherPriorityThan(targetRoleOpt.get());
    }

    /**
     * Computes effective permissions by OR-ing all assigned role permissions.
     */
    private int computeEffectivePermissions(UUID guildId, UUID playerId) {
        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guildId, playerId);
        int permissions = 0;
        for (String roleId : roleIds) {
            Optional<GuildRole> role = roleRepository.findById(roleId);
            if (role.isPresent()) {
                permissions |= role.get().getPermissions();
            }
        }
        return permissions;
    }

    /**
     * Gets the highest priority role for a member.
     */
    private Optional<GuildRole> getHighestPriorityRole(UUID guildId, UUID playerId) {
        List<String> roleIds = memberRoleRepository.getMemberRoleIds(guildId, playerId);
        GuildRole highestRole = null;

        for (String roleId : roleIds) {
            Optional<GuildRole> roleOpt = roleRepository.findById(roleId);
            if (roleOpt.isPresent()) {
                GuildRole role = roleOpt.get();
                if (highestRole == null || role.getPriority() > highestRole.getPriority()) {
                    highestRole = role;
                }
            }
        }

        return Optional.ofNullable(highestRole);
    }
}
