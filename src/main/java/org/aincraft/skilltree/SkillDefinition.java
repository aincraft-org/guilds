package org.aincraft.skilltree;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration record for a skill definition.
 * Loaded from config and never modified at runtime.
 * Single Responsibility: Represent skill configuration.
 *
 * @param id unique skill identifier
 * @param name human-readable skill name
 * @param description detailed skill description
 * @param branch the skill tree branch (ECONOMY, TERRITORY, COMBAT)
 * @param spCost skill points required to unlock
 * @param prerequisites list of prerequisite skill IDs (empty if none)
 * @param effect the buff effect this skill provides
 */
public record SkillDefinition(
    String id,
    String name,
    String description,
    SkillBranch branch,
    int spCost,
    List<String> prerequisites,
    SkillEffect effect
) {
    /**
     * Compact constructor that validates and normalizes the record.
     */
    public SkillDefinition {
        Objects.requireNonNull(id, "Skill ID cannot be null");
        Objects.requireNonNull(name, "Skill name cannot be null");
        Objects.requireNonNull(description, "Skill description cannot be null");
        Objects.requireNonNull(branch, "Branch cannot be null");
        Objects.requireNonNull(effect, "Effect cannot be null");

        if (spCost < 0) {
            throw new IllegalArgumentException("SP cost cannot be negative");
        }

        if (id.isBlank()) {
            throw new IllegalArgumentException("Skill ID cannot be blank");
        }

        // Make prerequisites unmodifiable
        prerequisites = prerequisites == null ? Collections.emptyList() :
            Collections.unmodifiableList(prerequisites);
    }

    /**
     * Checks if this skill has prerequisites.
     *
     * @return true if prerequisites list is not empty
     */
    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }

    /**
     * Checks if a specific skill is a prerequisite of this skill.
     *
     * @param skillId the skill ID to check
     * @return true if the skill is in prerequisites
     */
    public boolean isPrerequisite(String skillId) {
        return prerequisites.contains(skillId);
    }
}
