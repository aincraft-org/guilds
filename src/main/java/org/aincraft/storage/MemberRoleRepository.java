package org.aincraft.storage;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing member-role assignments.
 */
public interface MemberRoleRepository {
    /**
     * Assigns a role to a member.
     */
    void assignRole(UUID guildId, UUID playerId, String roleId);

    /**
     * Unassigns a role from a member.
     */
    void unassignRole(UUID guildId, UUID playerId, String roleId);

    /**
     * Gets all role IDs assigned to a member.
     */
    List<String> getMemberRoleIds(UUID guildId, UUID playerId);

    /**
     * Gets all member UUIDs that have a specific role.
     */
    List<UUID> getMembersWithRole(String roleId);

    /**
     * Checks if a member has a specific role assigned.
     */
    boolean hasMemberRole(UUID guildId, UUID playerId, String roleId);

    /**
     * Removes all role assignments for a member (for member removal).
     */
    void removeAllMemberRoles(UUID guildId, UUID playerId);

    /**
     * Removes all assignments for a specific role (for role deletion).
     */
    void removeAllByRole(String roleId);

    /**
     * Removes all role assignments for a guild (for guild deletion).
     */
    void removeAllByGuild(UUID guildId);
}
