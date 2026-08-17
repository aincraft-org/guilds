package dev.mintychochip.territory.storage;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for unit tests. Not a production backend. */
final class MemoryGuildStorageStore implements GuildStorageStore {
    private final ConcurrentHashMap<String, GuildStorageDocument> documents = new ConcurrentHashMap<>();

    @Override
    public Optional<GuildStorageDocument> load(String guildId) {
        return Optional.ofNullable(documents.get(guildId));
    }

    @Override
    public void save(GuildStorageDocument document) {
        documents.put(document.guildId(), document);
    }
}
