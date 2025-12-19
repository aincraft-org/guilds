package org.aincraft.skilltree;

import java.util.Objects;

/**
 * Immutable result type for skill unlock operations.
 * Provides structured feedback on whether a skill unlock succeeded and why.
 * Single Responsibility: Represent skill unlock operation result.
 *
 * @param success whether the unlock succeeded
 * @param message human-readable result message
 * @param skill the skill definition (only populated on success)
 */
public record SkillUnlockResult(
    boolean success,
    String message,
    SkillDefinition skill
) {
    /**
     * Compact constructor that validates the record.
     */
    public SkillUnlockResult {
        Objects.requireNonNull(message, "Message cannot be null");
        if (success && skill == null) {
            throw new IllegalArgumentException("Skill must be present on successful unlock");
        }
    }

    /**
     * Creates a successful unlock result.
     *
     * @param skill the unlocked skill
     * @return success result
     */
    public static SkillUnlockResult success(SkillDefinition skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");
        return new SkillUnlockResult(true, "Skill '" + skill.name() + "' unlocked", skill);
    }

    /**
     * Creates a failure result with generic message.
     *
     * @param message the failure reason
     * @return failure result
     */
    public static SkillUnlockResult failure(String message) {
        return new SkillUnlockResult(false, message, null);
    }

    /**
     * Creates a failure due to insufficient skill points.
     *
     * @param available available skill points
     * @param required required skill points
     * @return failure result
     */
    public static SkillUnlockResult insufficientSp(int available, int required) {
        return failure("Insufficient skill points (have " + available + ", need " + required + ")");
    }

    /**
     * Creates a failure due to missing prerequisites.
     *
     * @param skillId the skill ID
     * @param missingPrereq the missing prerequisite skill ID
     * @return failure result
     */
    public static SkillUnlockResult missingPrerequisite(String skillId, String missingPrereq) {
        return failure("Cannot unlock '" + skillId + "': missing prerequisite '" + missingPrereq + "'");
    }

    /**
     * Creates a failure because skill is already unlocked.
     *
     * @param skillId the skill ID
     * @return failure result
     */
    public static SkillUnlockResult alreadyUnlocked(String skillId) {
        return failure("Skill '" + skillId + "' is already unlocked");
    }

    /**
     * Creates a failure because skill definition does not exist.
     *
     * @param skillId the skill ID
     * @return failure result
     */
    public static SkillUnlockResult skillNotFound(String skillId) {
        return failure("Skill '" + skillId + "' not found in registry");
    }
}
