package org.aincraft.guilds.territory.model;

/**
 * Lifecycle status of a territory policy.
 */
public enum PolicyStatus {
    /** Open for votes / decree. */
    PROPOSED,
    /** Adopted under the form's decision rules. */
    PASSED,
    /** Defeated under the form's decision rules. */
    REJECTED;

    public static PolicyStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("policy status is required");
        }
        return PolicyStatus.valueOf(raw.trim().toUpperCase());
    }

    public boolean isTerminal() {
        return this == PASSED || this == REJECTED;
    }
}
