package org.aincraft.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.GuildInvite;

/**
 * Repository for guild invite persistence.
 */
public interface InviteRepository {

    /**
     * Saves a guild invite.
     *
     * @param invite the invite to save
     */
    void save(GuildInvite invite);

    /**
     * Finds an invite by ID.
     *
     * @param inviteId the invite ID
     * @return the invite, or empty if not found
     */
    Optional<GuildInvite> findById(String inviteId);

    /**
     * Finds all invites for a guild.
     *
     * @param guildId the guild ID
     * @return list of invites
     */
    List<GuildInvite> findByGuildId(UUID guildId);

    /**
     * Finds all invites received by a player.
     *
     * @param inviteeId the player UUID
     * @return list of invites
     */
    List<GuildInvite> findByInviteeId(UUID inviteeId);

    /**
     * Finds an active invite for a specific guild and player combination.
     *
     * @param guildId the guild ID
     * @param inviteeId the player UUID
     * @return the invite, or empty if not found
     */
    Optional<GuildInvite> findActiveInvite(UUID guildId, UUID inviteeId);

    /**
     * Counts pending invites for a guild.
     *
     * @param guildId the guild ID
     * @return number of pending invites
     */
    int countPendingInvites(UUID guildId);

    /**
     * Deletes an invite by ID.
     *
     * @param inviteId the invite ID
     */
    void delete(String inviteId);

    /**
     * Deletes all expired invites.
     */
    void deleteExpired();

    /**
     * Deletes all invites for a guild (used when guild is deleted).
     *
     * @param guildId the guild ID
     */
    void deleteByGuildId(UUID guildId);
}
