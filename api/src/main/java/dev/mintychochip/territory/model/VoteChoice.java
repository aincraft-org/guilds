package dev.mintychochip.territory.model;

/** A seat-holder's ballot on a policy. */
public enum VoteChoice {
    /** Vote in favor. */
    YES,
    /** Vote against. */
    NO,
    /** Decline to support either side. */
    ABSTAIN;

    /** Parses a persisted vote choice.
     * @param raw persisted choice name
     * @return the matching choice
     * @throws IllegalArgumentException if {@code raw} is blank or invalid
     */
    public static VoteChoice fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("vote choice is required");
        }
        return VoteChoice.valueOf(raw.trim().toUpperCase());
    }
}
