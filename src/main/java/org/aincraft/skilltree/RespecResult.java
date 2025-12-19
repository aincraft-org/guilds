package org.aincraft.skilltree;

import java.util.Objects;

/**
 * Immutable result type for respec operations.
 * Provides structured feedback on whether a respec succeeded and how many SP were restored.
 * Single Responsibility: Represent respec operation result.
 *
 * @param success whether the respec succeeded
 * @param message human-readable result message
 * @param spRestored the number of skill points restored (0 if unsuccessful)
 */
public record RespecResult(
    boolean success,
    String message,
    int spRestored
) {
    /**
     * Compact constructor that validates the record.
     */
    public RespecResult {
        Objects.requireNonNull(message, "Message cannot be null");
        if (spRestored < 0) {
            throw new IllegalArgumentException("SP restored cannot be negative");
        }
        if (success && spRestored == 0) {
            throw new IllegalArgumentException("Successful respec must restore SP");
        }
    }

    /**
     * Creates a successful respec result.
     *
     * @param spRestored the amount of skill points restored
     * @return success result
     */
    public static RespecResult success(int spRestored) {
        if (spRestored <= 0) {
            throw new IllegalArgumentException("SP restored must be positive");
        }
        return new RespecResult(true, "Skill tree reset. " + spRestored + " SP restored.", spRestored);
    }

    /**
     * Creates a failure result with generic message.
     *
     * @param message the failure reason
     * @return failure result
     */
    public static RespecResult failure(String message) {
        return new RespecResult(false, message, 0);
    }

    /**
     * Creates a failure due to respec being disabled.
     *
     * @return failure result
     */
    public static RespecResult disabled() {
        return failure("Respecs are disabled on this server");
    }

    /**
     * Creates a failure due to insufficient vault materials.
     *
     * @param material the material name
     * @param have the amount available
     * @param need the amount required
     * @return failure result
     */
    public static RespecResult insufficientMaterials(String material, int have, int need) {
        return failure("Insufficient " + material + " in vault (have " + have + ", need " + need + ")");
    }

    /**
     * Creates a failure due to vault access failure.
     *
     * @return failure result
     */
    public static RespecResult vaultAccessFailed() {
        return failure("Failed to access guild vault for respec materials");
    }

    /**
     * Creates a failure due to material consumption failure.
     *
     * @return failure result
     */
    public static RespecResult materialConsumptionFailed() {
        return failure("Failed to consume materials from vault");
    }

    /**
     * Creates a failure because guild vault does not exist.
     *
     * @return failure result
     */
    public static RespecResult vaultNotFound() {
        return failure("Guild vault not found");
    }
}
