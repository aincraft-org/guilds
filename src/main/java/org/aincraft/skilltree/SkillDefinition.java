package org.aincraft.skilltree;

import java.util.List;
import java.util.Objects;

/**
 * Represents the definition of a single skill in the guild skill tree.
 * This record is immutable and defines static skill properties that don't change per guild.
 * Guild progress is tracked separately in GuildSkillTree.
 *
 * @param id unique identifier for the skill (e.g., "eco_xp_boost_1")
 * @param name display name of the skill (e.g., "XP Insight I")
 * @param description description text explaining the skill's effect
 * @param branch which skill tree branch this skill belongs to
 * @param tier vertical position in the tree (1-based, higher = further down)
 * @param spCost skill points required to unlock this skill
 * @param prerequisites list of skill IDs that must be unlocked first
 * @param effect the buff effect this skill grants
 */
public record SkillDefinition(
    String id,
    String name,
    String description,
    SkillBranch branch,
    int tier,
    int spCost,
    List<String> prerequisites,
    SkillEffect effect
) {
    /**
     * Compact constructor for validation of SkillDefinition records.
     */
    public SkillDefinition {
        Objects.requireNonNull(id, "Skill ID cannot be null");
        Objects.requireNonNull(name, "Skill name cannot be null");
        Objects.requireNonNull(description, "Skill description cannot be null");
        Objects.requireNonNull(branch, "Skill branch cannot be null");
        Objects.requireNonNull(effect, "Skill effect cannot be null");

        if (tier < 1) {
            throw new IllegalArgumentException("Tier must be at least 1");
        }
        if (spCost < 1) {
            throw new IllegalArgumentException("SP cost must be at least 1");
        }

        // Defensive copy for immutability
        prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
    }
}
