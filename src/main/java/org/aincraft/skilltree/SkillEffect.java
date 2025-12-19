package org.aincraft.skilltree;

/**
 * Immutable record representing the effect provided by a skill.
 * Effects map to buff categories and apply permanent bonuses to guilds.
 * Single Responsibility: Represent skill effect configuration.
 *
 * @param category the buff category ID (e.g., "SKILL_XP_MULTIPLIER")
 * @param value the additive effect value (0.05 = +5%)
 * @param displayName human-readable name for UI display
 */
public record SkillEffect(
    String category,
    double value,
    String displayName
) {
}
