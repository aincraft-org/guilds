package org.aincraft.storage;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages player-to-guild mappings to enforce one guild per player.
 * Single Responsibility: Player guild membership tracking only.
 */
public interface PlayerGuildMapping {
    void addPlayerToGuild(UUID playerId, UUID guildId);
    void removePlayerFromGuild(UUID playerId);
    Optional<UUID> getPlayerGuildId(UUID playerId);
    boolean isPlayerInGuild(UUID playerId);
}
