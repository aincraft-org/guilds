package org.aincraft.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.GuildPermission;
import org.aincraft.MemberPermissions;

/**
 * Repository for managing guild member permissions.
 * Stores per-member permission bitfields for each guild.
 */
public interface GuildMemberRepository {
    /**
     * Adds a member to a guild with the specified permissions.
     */
    void addMember(UUID guildId, UUID playerId, MemberPermissions permissions);

    /**
     * Removes a member from a guild.
     */
    void removeMember(UUID guildId, UUID playerId);

    /**
     * Removes all members from a guild (for guild deletion).
     */
    void removeAllMembers(UUID guildId);

    /**
     * Gets the permissions for a specific member in a guild.
     */
    Optional<MemberPermissions> getPermissions(UUID guildId, UUID playerId);

    /**
     * Sets the permissions for a specific member.
     */
    void setPermissions(UUID guildId, UUID playerId, MemberPermissions permissions);

    /**
     * Gets all member UUIDs in a guild that have a specific permission.
     */
    List<UUID> getMembersWithPermission(UUID guildId, GuildPermission permission);

    /**
     * Gets the join date timestamp for a member in a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return Optional containing the join timestamp, or empty if member not found or timestamp not set
     */
    Optional<Long> getMemberJoinDate(UUID guildId, UUID playerId);
}
