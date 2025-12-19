package org.aincraft.skilltree;

import com.google.inject.Singleton;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Provides skill buff value lookups for BuffApplicationService.
 * Skills provide permanent buffs that contribute to guild bonuses.
 * Single Responsibility: Manage skill buff value calculations for guilds.
 *
 * Note: Actual buff application is handled by the repository layer.
 * This service provides values that BuffApplicationService uses in its calculations.
 */
@Singleton
public class SkillBuffProvider {
    /**
     * Gets the bonus value from skills for a specific buff category.
     * Skills provide additive bonuses to guild buffs.
     *
     * @param guildId the guild ID
     * @param categoryId the buff category ID (e.g., "SKILL_XP_MULTIPLIER")
     * @return the bonus value (0.0 if no skill buff exists for this category)
     */
    public double getSkillBonusValue(UUID guildId, String categoryId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        // Placeholder implementation - in future versions, would query skill
        // repository for unlocked skills and sum their effects for the category
        // For now, returns 0.0 (no skill bonuses applied yet)
        return 0.0;
    }
}
