package org.aincraft.storage;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.aincraft.Guild;

/**
 * In-memory implementation of GuildRepository for testing.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class InMemoryGuildRepository implements GuildRepository {

    private final Map<String, Guild> guildsById = new ConcurrentHashMap<>();
    private final Map<String, Guild> guildsByName = new ConcurrentHashMap<>();

    @Override
    public void save(Guild guild) {
        Objects.requireNonNull(guild, "Guild cannot be null");
        guildsById.put(guild.getId(), guild);
        guildsByName.put(guild.getName().toLowerCase(), guild);
    }

    @Override
    public void delete(UUID guildId) {
        Guild guild = guildsById.remove(guildId);
        if (guild != null) {
            guildsByName.remove(guild.getName().toLowerCase());
        }
    }

    @Override
    public Optional<Guild> findById(UUID guildId) {
        return Optional.ofNullable(guildsById.get(guildId));
    }

    @Override
    public Optional<Guild> findByName(String name) {
        return Optional.ofNullable(guildsByName.get(name.toLowerCase()));
    }

    @Override
    public List<Guild> findAll() {
        return new ArrayList<>(guildsById.values());
    }

    /**
     * Clears all stored guilds. Useful for test cleanup.
     */
    public void clear() {
        guildsById.clear();
        guildsByName.clear();
    }

    /**
     * Returns the number of stored guilds.
     */
    public int size() {
        return guildsById.size();
    }
}
