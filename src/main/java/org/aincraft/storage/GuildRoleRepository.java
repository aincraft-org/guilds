package org.aincraft.storage;

import org.aincraft.GuildRole;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing guild roles.
 */
public interface GuildRoleRepository {
    /**
     * Saves a role (create or update).
     */
    void save(GuildRole role);

    /**
     * Deletes a role by ID.
     */
    void delete(String roleId);

    /**
     * Finds a role by its ID.
     */
    Optional<GuildRole> findById(String roleId);

    /**
     * Finds all roles for a guild.
     */
    List<GuildRole> findByGuildId(String guildId);

    /**
     * Finds a role by guild ID and name.
     */
    Optional<GuildRole> findByGuildAndName(String guildId, String name);

    /**
     * Deletes all roles for a guild (for guild deletion).
     */
    void deleteAllByGuild(String guildId);
}
