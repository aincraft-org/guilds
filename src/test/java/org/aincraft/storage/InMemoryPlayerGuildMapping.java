package org.aincraft.storage;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PlayerGuildMapping for testing.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class InMemoryPlayerGuildMapping implements PlayerGuildMapping {

    private final Map<UUID, String> playerToGuild = new ConcurrentHashMap<>();

    @Override
    public void addPlayerToGuild(UUID playerId, UUID guildId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        playerToGuild.put(playerId, guildId);
    }

    @Override
    public void removePlayerFromGuild(UUID playerId) {
        playerToGuild.remove(playerId);
    }

    @Override
    public Optional<String> getPlayerGuildId(UUID playerId) {
        return Optional.ofNullable(playerToGuild.get(playerId));
    }

    @Override
    public boolean isPlayerInGuild(UUID playerId) {
        return playerToGuild.containsKey(playerId);
    }

    /**
     * Clears all player-guild mappings. Useful for test cleanup.
     */
    public void clear() {
        playerToGuild.clear();
    }

    /**
     * Returns the number of stored mappings.
     */
    public int size() {
        return playerToGuild.size();
    }

    /**
     * Gets all players in a specific guild.
     */
    public Set<UUID> getPlayersInGuild(UUID guildId) {
        Set<UUID> players = new HashSet<>();
        for (Map.Entry<UUID, String> entry : playerToGuild.entrySet()) {
            if (entry.getValue().equals(guildId)) {
                players.add(entry.getKey());
            }
        }
        return players;
    }
}
