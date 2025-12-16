package org.aincraft.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player-to-guild mappings to enforce one guild per player.
 * Single Responsibility: Player guild membership tracking only.
 */
public interface PlayerGuildMapping {
    void addPlayerToGuild(UUID playerId, String guildId);
    void removePlayerFromGuild(UUID playerId);
    Optional<String> getPlayerGuildId(UUID playerId);
    boolean isPlayerInGuild(UUID playerId);
}
