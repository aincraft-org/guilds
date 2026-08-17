package com.azoth.territory.model;

/** Lifecycle status of a territory policy. */
public enum PolicyStatus {
    /** Open for votes / decree. */
    PROPOSED,
    /** Adopted under the form's decision rules. */
    PASSED,
    /** Defeated under the form's decision rules. */
    REJECTED;

    /** Parses a persisted policy status.
     * @param raw persisted status name
     * @return the matching status
     * @throws IllegalArgumentException if {@code raw} is blank or invalid
     */
    public static PolicyStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("policy status is required");
        }
        return PolicyStatus.valueOf(raw.trim().toUpperCase());
    }

    /**
     * Determines whether this status ends policy processing.
     * @return {@code true} for passed or rejected policies
     */
    public boolean isTerminal() {
        return this == PASSED || this == REJECTED;
    }
}
