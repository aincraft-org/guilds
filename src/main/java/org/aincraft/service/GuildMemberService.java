package org.aincraft.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.LeaveResult;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade service for guild member operations.
 * Delegates to GuildService for all operations.
 */
@Singleton
public class GuildMemberService {
    private final GuildService guildService;

    @Inject
    public GuildMemberService(GuildService guildService) {
        this.guildService = Objects.requireNonNull(guildService);
    }

    /**
     * Gets the guild a player belongs to.
     *
     * @param playerId the player UUID
     * @return the guild, or null if not in a guild
     */
    public Guild getPlayerGuild(UUID playerId) {
        return guildService.getPlayerGuild(playerId);
    }

    /**
     * Adds a player to a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return true if joined successfully
     */
    public boolean joinGuild(UUID guildId, UUID playerId) {
        return guildService.joinGuild(guildId, playerId);
    }

    /**
     * Removes a player from a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the result of the leave operation
     */
    public LeaveResult leaveGuild(UUID guildId, UUID playerId) {
        return guildService.leaveGuild(guildId, playerId);
    }

    /**
     * Kicks a player from a guild.
     *
     * @param guildId the guild ID
     * @param kickerId the kicker's UUID
     * @param targetId the target's UUID
     * @return true if kicked successfully
     */
    public boolean kickMember(UUID guildId, UUID kickerId, UUID targetId) {
        return guildService.kickMember(guildId, kickerId, targetId);
    }

    /**
     * Gets the join date for a guild member.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the join date as epoch millis, or empty if not a member
     */
    public Optional<Long> getMemberJoinDate(UUID guildId, UUID playerId) {
        return guildService.getMemberJoinDate(guildId, playerId);
    }
}
