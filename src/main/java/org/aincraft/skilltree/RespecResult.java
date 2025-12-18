package org.aincraft.skilltree;

/**
 * Result record for respec operations.
 * Encapsulates the outcome of attempting to reset a guild's skill tree,
 * including success status, error messages, and points refunded.
 *
 * @param success whether the respec operation succeeded
 * @param errorMessage description of the failure (null if successful)
 * @param refundedPoints the number of skill points refunded (0 if unsuccessful)
 */
public record RespecResult(
    boolean success,
    String errorMessage,
    int refundedPoints
) {
    /**
     * Creates a successful respec result.
     * @param refundedPoints the number of skill points that were refunded
     * @return a success result
     */
    public static RespecResult success(int refundedPoints) {
        return new RespecResult(true, null, refundedPoints);
    }

    /**
     * Creates a generic failure result.
     * @param errorMessage the error message
     * @return a failure result
     */
    public static RespecResult failure(String errorMessage) {
        return new RespecResult(false, errorMessage, 0);
    }

    /**
     * Creates a failure result for permission denial.
     * @return a permission denied result
     */
    public static RespecResult noPermission() {
        return failure("You don't have permission to respec skills");
    }

    /**
     * Creates a failure result for insufficient materials.
     * @param material the material name
     * @param have the amount currently owned
     * @param need the amount required
     * @return an insufficient materials result
     */
    public static RespecResult insufficientMaterials(String material, int have, int need) {
        return failure(String.format("Need %dx %s (have %d)", need, material, have));
    }

    /**
     * Creates a failure result when there are no skills to reset.
     * @return a no skills to reset result
     */
    public static RespecResult noSkillsToReset() {
        return failure("No skills to reset");
    }
}
