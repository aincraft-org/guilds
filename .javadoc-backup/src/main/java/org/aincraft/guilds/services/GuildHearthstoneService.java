package org.aincraft.guilds.services;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for teleporting to a player's guild spawn using a hearthstone-like item.
 * <p>
 * Destination is the existing guild spawn; no new player-bound homes.
 */
public interface GuildHearthstoneService {

    /**
     * Teleport the player to their guild spawn if allowed.
     *
     * @param playerUuid player UUID
     * @return {@code true} if teleport executed
     */
    boolean teleportToGuildSpawn(UUID playerUuid);

    /**
     * Remaining cooldown seconds for a player.
     */
    long remainingCooldownSeconds(UUID playerUuid);

    /**
     * Set cooldown for a player.
     */
    void setCooldown(UUID playerUuid, long seconds);
}
