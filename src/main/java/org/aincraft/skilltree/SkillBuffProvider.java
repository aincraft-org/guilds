package org.aincraft.skilltree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;

import java.util.Objects;
import java.util.Set;

/**
 * Provides aggregated skill buff values for guilds.
 * Uses additive stacking: skill1 (5%) + skill2 (10%) = 15% total.
 */
@Singleton
public class SkillBuffProvider {

    private final GuildSkillTreeRepository repository;
    private final SkillTreeRegistry registry;

    @Inject
    public SkillBuffProvider(GuildSkillTreeRepository repository, SkillTreeRegistry registry) {
        this.repository = Objects.requireNonNull(repository);
        this.registry = Objects.requireNonNull(registry);
    }

    /**
     * Gets the total skill bonus for a category.
     * Returns the additive sum of all unlocked skill effects for that category.
     *
     * @param guildId the guild ID
     * @param categoryId the buff category (e.g., "XP_MULTIPLIER")
     * @return total bonus as decimal (e.g., 0.15 for 15%)
     */
    public double getSkillBonusValue(String guildId, String categoryId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        Set<String> unlockedSkills = repository.getUnlockedSkills(guildId);
        if (unlockedSkills.isEmpty()) {
            return 0.0;
        }

        double totalBonus = 0.0;

        for (String skillId : unlockedSkills) {
            var skillOpt = registry.getSkill(skillId);
            if (skillOpt.isPresent()) {
                SkillDefinition skill = skillOpt.get();
                if (skill.effect().buffCategory().equals(categoryId)) {
                    totalBonus += skill.effect().value();
                }
            }
        }

        return totalBonus;
    }

    /**
     * Checks if a guild has any buff in a category from skills.
     *
     * @param guildId the guild ID
     * @param categoryId the buff category
     * @return true if guild has any skill buff for this category
     */
    public boolean hasSkillBuff(String guildId, String categoryId) {
        return getSkillBonusValue(guildId, categoryId) > 0.0;
    }

    /**
     * Gets a formatted display string for skill bonuses in a category.
     *
     * @param guildId the guild ID
     * @param categoryId the buff category
     * @return formatted string like "+15% from skills" or empty if no bonus
     */
    public String getSkillBonusDisplay(String guildId, String categoryId) {
        double bonus = getSkillBonusValue(guildId, categoryId);
        if (bonus <= 0.0) {
            return "";
        }
        return String.format("+%.0f%% from skills", bonus * 100);
    }
}
