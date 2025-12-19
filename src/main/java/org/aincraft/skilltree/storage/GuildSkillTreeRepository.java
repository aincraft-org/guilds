package org.aincraft.skilltree.storage;

import org.aincraft.skilltree.GuildSkillTree;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for guild skill tree persistence.
 * Defines contract for saving, loading, and managing skill tree data.
 * Single Responsibility: Skill tree data access abstraction.
 * Dependency Inversion: Callers depend on this interface, not concrete implementations.
 */
public interface GuildSkillTreeRepository {
    /**
     * Saves or updates a guild's skill tree.
     *
     * @param tree the skill tree to save (cannot be null)
     */
    void save(GuildSkillTree tree);

    /**
     * Retrieves a guild's skill tree.
     *
     * @param guildId the guild ID (cannot be null)
     * @return the skill tree if found, or empty Optional
     */
    Optional<GuildSkillTree> findByGuildId(UUID guildId);

    /**
     * Deletes a guild's skill tree and unlocked skills.
     *
     * @param guildId the guild ID (cannot be null)
     */
    void delete(UUID guildId);

    /**
     * Records that a skill was unlocked.
     *
     * @param guildId the guild ID (cannot be null)
     * @param skillId the skill ID (cannot be null)
     * @param unlockedAt the unlock timestamp (milliseconds since epoch)
     */
    void unlockSkill(UUID guildId, String skillId, long unlockedAt);

    /**
     * Clears all unlocked skills for a guild (used in respec).
     *
     * @param guildId the guild ID (cannot be null)
     */
    void clearUnlockedSkills(UUID guildId);

    /**
     * Retrieves all unlocked skill IDs for a guild.
     *
     * @param guildId the guild ID (cannot be null)
     * @return unmodifiable set of unlocked skill IDs
     */
    Set<String> getUnlockedSkills(UUID guildId);
}
