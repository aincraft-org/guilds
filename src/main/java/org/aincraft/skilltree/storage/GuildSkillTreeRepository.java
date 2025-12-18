package org.aincraft.skilltree.storage;

import org.aincraft.skilltree.GuildSkillTree;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for guild skill tree persistence.
 */
public interface GuildSkillTreeRepository {

    /**
     * Saves a guild's skill tree state.
     */
    void save(GuildSkillTree skillTree);

    /**
     * Finds a guild's skill tree by guild ID.
     */
    Optional<GuildSkillTree> findByGuildId(String guildId);

    /**
     * Deletes a guild's skill tree and all unlocked skills.
     */
    void delete(String guildId);

    /**
     * Unlocks a skill for a guild.
     */
    void unlockSkill(String guildId, String skillId);

    /**
     * Gets all unlocked skill IDs for a guild.
     */
    Set<String> getUnlockedSkills(String guildId);

    /**
     * Deletes all unlocked skills for a guild (used for respec).
     */
    void deleteAllSkills(String guildId);
}
