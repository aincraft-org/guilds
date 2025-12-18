package org.aincraft.skilltree;

/**
 * Result record for skill unlock operations.
 * Encapsulates the outcome of attempting to unlock a skill, including success status,
 * error messages, and the skill that was unlocked (if successful).
 *
 * @param success whether the unlock operation succeeded
 * @param errorMessage description of the failure (null if successful)
 * @param skill the skill that was unlocked (null if unsuccessful)
 */
public record SkillUnlockResult(
    boolean success,
    String errorMessage,
    SkillDefinition skill
) {
    /**
     * Creates a successful unlock result.
     * @param skill the skill that was unlocked
     * @return a success result
     */
    public static SkillUnlockResult success(SkillDefinition skill) {
        return new SkillUnlockResult(true, null, skill);
    }

    /**
     * Creates a generic failure result.
     * @param errorMessage the error message
     * @return a failure result
     */
    public static SkillUnlockResult failure(String errorMessage) {
        return new SkillUnlockResult(false, errorMessage, null);
    }

    /**
     * Creates a failure result for permission denial.
     * @return a permission denied result
     */
    public static SkillUnlockResult noPermission() {
        return failure("You don't have permission to manage skills");
    }

    /**
     * Creates a failure result for skill not found.
     * @return a skill not found result
     */
    public static SkillUnlockResult skillNotFound() {
        return failure("Skill not found");
    }

    /**
     * Creates a failure result for already unlocked skill.
     * @return an already unlocked result
     */
    public static SkillUnlockResult alreadyUnlocked() {
        return failure("Skill is already unlocked");
    }

    /**
     * Creates a failure result for insufficient skill points.
     * @param available the skill points currently available
     * @param required the skill points required
     * @return an insufficient points result
     */
    public static SkillUnlockResult insufficientSP(int available, int required) {
        return failure(String.format("Not enough skill points (have %d, need %d)", available, required));
    }

    /**
     * Creates a failure result for missing prerequisites.
     * @param missingSkill the name or ID of the missing prerequisite skill
     * @return a missing prerequisites result
     */
    public static SkillUnlockResult missingPrerequisites(String missingSkill) {
        return failure("Missing prerequisite: " + missingSkill);
    }
}
