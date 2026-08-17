package dev.mintychochip.territory.model;

import java.util.Objects;

/**
 * One opaque voter's ballot on a policy.
 *
 * @param voterId opaque voter identifier
 * @param choice selected vote choice
 * @param castAtEpochMs time at which the vote was cast, in epoch milliseconds
 */
public record PolicyVote(String voterId, VoteChoice choice, long castAtEpochMs) {

    /** Validates and normalizes a policy vote.
     * @throws IllegalArgumentException if the voter identifier is blank
     * @throws NullPointerException if the choice is {@code null}
     */
    public PolicyVote {
        if (voterId == null || voterId.isBlank()) {
            throw new IllegalArgumentException("voterId is required");
        }
        voterId = voterId.trim();
        Objects.requireNonNull(choice, "choice");
    }

    /** Returns the canonical textual representation.
     * @return a concise description of this vote
     */
    @Override
    public String toString() {
        return "PolicyVote{voter='" + voterId + "', choice=" + choice + '}';
    }
}
