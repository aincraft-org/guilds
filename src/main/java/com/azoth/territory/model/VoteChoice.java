package com.azoth.territory.model;

/**
 * A seat-holder's ballot on a policy.
 */
public enum VoteChoice {
    YES,
    NO,
    ABSTAIN;

    public static VoteChoice fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("vote choice is required");
        }
        return VoteChoice.valueOf(raw.trim().toUpperCase());
    }
}
