package com.azoth.territory.model;

import java.util.Objects;

/**
 * One opaque voter's ballot on a policy.
 */
public final class PolicyVote {
    private final String voterId;
    private final VoteChoice choice;
    private final long castAtEpochMs;

    public PolicyVote(String voterId, VoteChoice choice, long castAtEpochMs) {
        if (voterId == null || voterId.isBlank()) {
            throw new IllegalArgumentException("voterId is required");
        }
        this.voterId = voterId.trim();
        this.choice = Objects.requireNonNull(choice, "choice");
        this.castAtEpochMs = castAtEpochMs;
    }

    public String voterId() {
        return voterId;
    }

    public VoteChoice choice() {
        return choice;
    }

    public long castAtEpochMs() {
        return castAtEpochMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PolicyVote that)) {
            return false;
        }
        return castAtEpochMs == that.castAtEpochMs
                && voterId.equals(that.voterId)
                && choice == that.choice;
    }

    @Override
    public int hashCode() {
        return Objects.hash(voterId, choice, castAtEpochMs);
    }

    @Override
    public String toString() {
        return "PolicyVote{voter='" + voterId + "', choice=" + choice + '}';
    }
}
