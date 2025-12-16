package org.aincraft.storage;

import org.aincraft.Guild;
import java.util.*;

/**
 * Manages guild storage and retrieval by ID and name.
 * Single Responsibility: Guild persistence only.
 */
public interface GuildRepository {
    void save(Guild guild);
    void delete(String guildId);
    Optional<Guild> findById(String guildId);
    Optional<Guild> findByName(String name);
    List<Guild> findAll();
}
