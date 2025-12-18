package org.aincraft.skilltree;

/**
 * Represents a buff effect that a skill grants.
 * Effects are stored as additive bonuses, not multipliers.
 *
 * @param buffCategory the category of the buff (e.g., "XP_MULTIPLIER", "CROP_GROWTH_SPEED")
 * @param value the bonus value as an additive modifier (e.g., 0.05 for 5% bonus)
 * @param displayName the human-readable name of the effect (e.g., "5% XP Boost")
 */
public record SkillEffect(
    String buffCategory,
    double value,
    String displayName
) {
    /**
     * Compact constructor for validation of SkillEffect records.
     */
    public SkillEffect {
        if (buffCategory == null || buffCategory.isBlank()) {
            throw new IllegalArgumentException("Buff category cannot be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be null or blank");
        }
    }
}
