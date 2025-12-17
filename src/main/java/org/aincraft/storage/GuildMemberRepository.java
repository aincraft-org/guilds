package org.aincraft.storage;

import org.aincraft.GuildPermission;
import org.aincraft.MemberPermissions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing guild member permissions.
 * Stores per-member permission bitfields for each guild.
 */
public interface GuildMemberRepository {
    /**
     * Adds a member to a guild with the specified permissions.
     */
    void addMember(String guildId, UUID playerId, MemberPermissions permissions);

    /**
     * Removes a member from a guild.
     */
    void removeMember(String guildId, UUID playerId);

    /**
     * Removes all members from a guild (for guild deletion).
     */
    void removeAllMembers(String guildId);

    /**
     * Gets the permissions for a specific member in a guild.
     */
    Optional<MemberPermissions> getPermissions(String guildId, UUID playerId);

    /**
     * Sets the permissions for a specific member.
     */
    void setPermissions(String guildId, UUID playerId, MemberPermissions permissions);

    /**
     * Gets all member UUIDs in a guild that have a specific permission.
     */
    List<UUID> getMembersWithPermission(String guildId, GuildPermission permission);

    /**
     * Gets the join date timestamp for a member in a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return Optional containing the join timestamp, or empty if member not found or timestamp not set
     */
    Optional<Long> getMemberJoinDate(String guildId, UUID playerId);
}
