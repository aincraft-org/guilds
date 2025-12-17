package org.aincraft;

import java.util.UUID;

/**
 * Represents an invitation for a player to join a guild.
 * Invites expire after 5 minutes.
 */
public record GuildInvite(
    String id,
    String guildId,
    UUID inviterId,
    UUID inviteeId,
    long createdAt,
    long expiresAt
) {
    private static final long EXPIRATION_MILLIS = 300_000L; // 5 minutes

    /**
     * Creates a new guild invite that expires in 5 minutes.
     *
     * @param guildId the guild ID
     * @param inviterId the player who sent the invite
     * @param inviteeId the player who received the invite
     * @return a new GuildInvite
     */
    public static GuildInvite create(String guildId, UUID inviterId, UUID inviteeId) {
        long now = System.currentTimeMillis();
        return new GuildInvite(
            UUID.randomUUID().toString(),
            guildId,
            inviterId,
            inviteeId,
            now,
            now + EXPIRATION_MILLIS
        );
    }

    /**
     * Checks if this invite has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Gets the remaining time in milliseconds before expiration.
     *
     * @return remaining milliseconds, or 0 if expired
     */
    public long remainingMillis() {
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
