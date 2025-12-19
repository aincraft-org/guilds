package org.aincraft.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;

/**
 * Manages guild storage and retrieval by ID and name.
 * Single Responsibility: Guild persistence only.
 */
public interface GuildRepository {
    void save(Guild guild);
    void delete(UUID guildId);
    Optional<Guild> findById(UUID guildId);
    Optional<Guild> findByName(String name);
    List<Guild> findAll();
}
