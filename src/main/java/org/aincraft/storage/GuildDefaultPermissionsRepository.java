package org.aincraft.storage;

import java.util.Optional;
import java.util.UUID;
import org.aincraft.GuildDefaultPermissions;
import org.aincraft.subregion.SubjectType;

/**
 * Manages guild default permissions storage and retrieval.
 * Single Responsibility: Guild default permissions persistence only.
 */
public interface GuildDefaultPermissionsRepository {
    /**
     * Saves guild default permissions (insert or update).
     *
     * @param permissions the permissions to save
     */
    void save(GuildDefaultPermissions permissions);

    /**
     * Finds permissions for a specific guild.
     *
     * @param guildId the guild ID
     * @return Optional containing the permissions, or empty if not found
     */
    Optional<GuildDefaultPermissions> findByGuildId(UUID guildId);

    /**
     * Deletes permissions for a guild.
     *
     * @param guildId the guild ID
     */
    void delete(UUID guildId);

    /**
     * Gets permissions for a specific relationship type, returning defaults if not found.
     *
     * @param guildId the guild ID
     * @param subjectType the relationship type (GUILD_ALLY, GUILD_ENEMY, GUILD_OUTSIDER)
     * @return the permissions bitfield, or 0 if guild not found
     */
    int getPermissions(UUID guildId, SubjectType subjectType);
}
