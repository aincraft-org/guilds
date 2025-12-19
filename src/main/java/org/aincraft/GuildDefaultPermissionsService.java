package org.aincraft;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.storage.GuildDefaultPermissionsRepository;
import org.aincraft.subregion.SubjectType;

/**
 * Service for managing guild default permissions.
 * Handles CRUD operations for guild relationship-based permissions.
 */
public class GuildDefaultPermissionsService {
    private final GuildDefaultPermissionsRepository repository;

    @Inject
    public GuildDefaultPermissionsService(GuildDefaultPermissionsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    /**
     * Gets permissions for a specific relationship type for a guild.
     * Returns guild-specific settings or global defaults if guild not found.
     *
     * @param guildId the guild ID
     * @param subjectType the relationship type (GUILD_ALLY, GUILD_ENEMY, GUILD_OUTSIDER)
     * @return the permissions bitfield
     */
    public int getPermissions(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");
        return repository.getPermissions(guildId, subjectType);
    }

    /**
     * Checks if a player has a specific permission for a relationship type.
     *
     * @param guildId the guild ID
     * @param subjectType the relationship type
     * @param permissionBit the permission bit to check
     * @return true if the player has the permission
     */
    public boolean hasPermission(UUID guildId, SubjectType subjectType, int permissionBit) {
        int permissions = getPermissions(guildId, subjectType);
        return (permissions & permissionBit) != 0;
    }

    /**
     * Gets or creates default permissions for a guild.
     *
     * @param guildId the guild ID
     * @return the guild's default permissions
     */
    public GuildDefaultPermissions getOrCreate(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<GuildDefaultPermissions> existing = repository.findByGuildId(guildId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create with global defaults
        GuildDefaultPermissions perms = new GuildDefaultPermissions(guildId, 4, 0, 0); // 4 = INTERACT
        repository.save(perms);
        return perms;
    }

    /**
     * Sets permissions for allies.
     *
     * @param guildId the guild ID
     * @param permissions the permissions bitfield
     */
    public void setAllyPermissions(UUID guildId, int permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        GuildDefaultPermissions perms = getOrCreate(guildId);
        perms.setAllyPermissions(permissions);
        repository.save(perms);
    }

    /**
     * Sets permissions for enemies.
     *
     * @param guildId the guild ID
     * @param permissions the permissions bitfield
     */
    public void setEnemyPermissions(UUID guildId, int permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        GuildDefaultPermissions perms = getOrCreate(guildId);
        perms.setEnemyPermissions(permissions);
        repository.save(perms);
    }

    /**
     * Sets permissions for outsiders.
     *
     * @param guildId the guild ID
     * @param permissions the permissions bitfield
     */
    public void setOutsiderPermissions(UUID guildId, int permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        GuildDefaultPermissions perms = getOrCreate(guildId);
        perms.setOutsiderPermissions(permissions);
        repository.save(perms);
    }

    /**
     * Resets permissions to global defaults.
     *
     * @param guildId the guild ID
     */
    public void resetToDefaults(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        GuildDefaultPermissions perms = new GuildDefaultPermissions(guildId, 4, 0, 0); // 4 = INTERACT
        repository.save(perms);
    }

    /**
     * Deletes permissions for a guild.
     *
     * @param guildId the guild ID
     */
    public void delete(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        repository.delete(guildId);
    }

    /**
     * Gets the current permissions for a guild.
     *
     * @param guildId the guild ID
     * @return the permissions object, or an empty Optional if not found
     */
    public Optional<GuildDefaultPermissions> getPermissionsForGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return repository.findByGuildId(guildId);
    }
}
