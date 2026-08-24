package org.aincraft.guilds.territory.model;

import java.util.Objects;

/**
 * One opaque voter's ballot on a policy.
 */
public record PolicyVote(String voterId, VoteChoice choice, long castAtEpochMs) {

    public PolicyVote {
        if (voterId == null || voterId.isBlank()) {
            throw new IllegalArgumentException("voterId is required");
        }
        voterId = voterId.trim();
        Objects.requireNonNull(choice, "choice");
    }

    @Override
    public String toString() {
        return "PolicyVote{voter='" + voterId + "', choice=" + choice + '}';
    }
}
