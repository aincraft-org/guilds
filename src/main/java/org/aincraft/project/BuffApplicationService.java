package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.ActiveBuffRepository;
import org.aincraft.skilltree.SkillBuffProvider;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class BuffApplicationService {

    private final ActiveBuffRepository buffRepository;
    private final BuffCategoryRegistry buffCategoryRegistry;
    private final SkillBuffProvider skillBuffProvider;

    @Inject
    public BuffApplicationService(
            ActiveBuffRepository buffRepository,
            BuffCategoryRegistry buffCategoryRegistry,
            SkillBuffProvider skillBuffProvider
    ) {
        this.buffRepository = Objects.requireNonNull(buffRepository);
        this.buffCategoryRegistry = Objects.requireNonNull(buffCategoryRegistry);
        this.skillBuffProvider = Objects.requireNonNull(skillBuffProvider);
    }

    /**
     * Gets the total XP multiplier for a guild.
     * Combines project buffs (multiplicative base) with skill buffs (additive).
     * Example: Project 1.25x + Skill 15% = 1.25 + 0.15 = 1.40x
     */
    public double getXpMultiplier(String guildId) {
        return getCombinedBuffValue(guildId, "XP_MULTIPLIER", 1.0);
    }

    /**
     * Gets the total luck bonus for a guild.
     * Combines project buffs with skill buffs (additive).
     */
    public double getLuckBonus(String guildId) {
        return getCombinedBuffValue(guildId, "LUCK_BONUS", 1.0);
    }

    /**
     * Gets the total crop growth multiplier for a guild.
     * Combines project buffs with skill buffs (additive).
     */
    public double getCropGrowthMultiplier(String guildId) {
        return getCombinedBuffValue(guildId, "CROP_GROWTH_SPEED", 1.0);
    }

    /**
     * Gets the total mob spawn multiplier for a guild.
     * Combines project buffs with skill buffs (additive).
     */
    public double getMobSpawnMultiplier(String guildId) {
        return getCombinedBuffValue(guildId, "MOB_SPAWN_RATE", 1.0);
    }

    /**
     * Gets the total protection multiplier for a guild.
     * Combines project buffs with skill buffs (additive).
     * For protection, lower values are better (damage reduction).
     */
    public double getProtectionMultiplier(String guildId) {
        // Protection is special: project buff is like 0.85 (15% reduction)
        // Skill bonus is 0.05 (5% reduction), so we subtract from project buff
        double projectBuff = getProjectBuffValue(guildId, "PROTECTION_BOOST", 1.0);
        double skillBonus = skillBuffProvider.getSkillBonusValue(guildId, "PROTECTION_BOOST");
        // Subtract skill bonus from multiplier (more reduction = lower multiplier)
        return Math.max(0.1, projectBuff - skillBonus);
    }

    /**
     * Gets the total damage boost multiplier for a guild.
     * Combines project buffs with skill buffs (additive).
     */
    public double getDamageBoostMultiplier(String guildId) {
        return getCombinedBuffValue(guildId, "DAMAGE_BOOST", 1.0);
    }

    /**
     * Gets combined buff value from projects and skills (additive stacking).
     * Formula: projectBuff + skillBonus
     *
     * @param guildId the guild ID
     * @param categoryId the buff category
     * @param defaultValue default value if no project buff
     * @return combined buff value
     */
    public double getCombinedBuffValue(String guildId, String categoryId, double defaultValue) {
        double projectBuff = getProjectBuffValue(guildId, categoryId, defaultValue);
        double skillBonus = skillBuffProvider.getSkillBonusValue(guildId, categoryId);
        return projectBuff + skillBonus;
    }

    /**
     * Gets only the project buff value (without skill bonuses).
     */
    public double getProjectBuffValue(String guildId, String categoryId, double defaultValue) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return defaultValue;
        }

        ActiveBuff buff = buffOpt.get();
        if (buff.isExpired()) {
            return defaultValue;
        }

        if (buff.categoryId().equals(categoryId)) {
            return buff.value();
        }

        return defaultValue;
    }

    /**
     * Gets only the skill buff bonus value.
     */
    public double getSkillBuffValue(String guildId, String categoryId) {
        return skillBuffProvider.getSkillBonusValue(guildId, categoryId);
    }

    /**
     * @deprecated Use getCombinedBuffValue() instead for combined project+skill buffs,
     *             or getProjectBuffValue() for project-only buffs.
     */
    @Deprecated
    public double getBuffValue(String guildId, String categoryId, double defaultValue) {
        return getProjectBuffValue(guildId, categoryId, defaultValue);
    }

    public boolean hasBuff(String guildId, String categoryId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return false;
        }

        ActiveBuff buff = buffOpt.get();
        return !buff.isExpired() && buff.categoryId().equals(categoryId);
    }


    public void cleanupExpiredBuffs() {
        buffRepository.deleteExpired();
    }
}
