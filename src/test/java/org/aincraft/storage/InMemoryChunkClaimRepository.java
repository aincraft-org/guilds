package org.aincraft.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.aincraft.ChunkKey;
import org.aincraft.map.ChunkClaimData;

/**
 * In-memory implementation of ChunkClaimRepository for testing.
 */
public class InMemoryChunkClaimRepository implements ChunkClaimRepository {

    private final Map<ChunkKey, ChunkClaimData> claims = new ConcurrentHashMap<>();

    @Override
    public boolean claim(ChunkKey chunk, UUID guildId, UUID claimedBy) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(claimedBy, "Claimer ID cannot be null");

        if (claims.containsKey(chunk)) {
            return false; // Already claimed
        }

        claims.put(chunk, new ChunkClaimData(guildId, claimedBy, System.currentTimeMillis()));
        return true;
    }

    @Override
    public boolean unclaim(ChunkKey chunk, UUID guildId) {
        ChunkClaimData data = claims.get(chunk);
        if (data == null || !data.guildId().equals(guildId)) {
            return false;
        }
        claims.remove(chunk);
        return true;
    }

    @Override
    public void unclaimAll(UUID guildId) {
        claims.entrySet().removeIf(entry -> entry.getValue().guildId().equals(guildId));
    }

    @Override
    public Optional<String> getOwner(ChunkKey chunk) {
        ChunkClaimData data = claims.get(chunk);
        return data != null ? Optional.of(data.guildId()) : Optional.empty();
    }

    @Override
    public List<ChunkKey> getGuildChunks(UUID guildId) {
        return claims.entrySet().stream()
                .filter(entry -> entry.getValue().guildId().equals(guildId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public int getChunkCount(UUID guildId) {
        return (int) claims.values().stream()
                .filter(data -> data.guildId().equals(guildId))
                .count();
    }

    @Override
    public Map<ChunkKey, ChunkClaimData> getOwnersForChunks(List<ChunkKey> chunks) {
        Map<ChunkKey, ChunkClaimData> result = new HashMap<>();
        for (ChunkKey chunk : chunks) {
            ChunkClaimData data = claims.get(chunk);
            if (data != null) {
                result.put(chunk, data);
            }
        }
        return result;
    }

    /**
     * Clears all claims. Useful for test cleanup.
     */
    public void clear() {
        claims.clear();
    }

    /**
     * Returns the total number of claimed chunks.
     */
    public int size() {
        return claims.size();
    }
}
