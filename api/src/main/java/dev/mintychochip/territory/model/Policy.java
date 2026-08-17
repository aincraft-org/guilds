package dev.mintychochip.territory.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A territory policy proposal: sovereign decision data for later enforcement wiring.
 * <p>
 * Lifecycle: {@link PolicyStatus#PROPOSED} → vote/decree → {@link PolicyStatus#PASSED}
 * or {@link PolicyStatus#REJECTED}.
 */
public final class Policy {
    private final String id;
    private final String title;
    private final String body;
    private final String proposerId;
    private final PolicyStatus status;
    private final Map<String, PolicyVote> votesByVoter;
    private final Long resolvedAtEpochMs;
    private final Long proposedAtEpochMs;

    /**
     * Creates a policy.
     *
     * @param id policy identifier
     * @param title policy title
     * @param body policy text
     * @param proposerId opaque proposer identifier
     * @param status lifecycle status
     * @param votes initial votes
     * @param resolvedAtEpochMs optional resolution time in epoch milliseconds
     * @param proposedAtEpochMs optional proposal time in epoch milliseconds
     */
    public Policy(
            String id,
            String title,
            String body,
            String proposerId,
            PolicyStatus status,
            List<PolicyVote> votes,
            Long resolvedAtEpochMs,
            Long proposedAtEpochMs
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("policy id is required");
        }
        this.id = id.trim();
        this.title = title == null ? "" : title.trim();
        this.body = body == null ? "" : body;
        if (proposerId == null || proposerId.isBlank()) {
            throw new IllegalArgumentException("proposerId is required");
        }
        this.proposerId = proposerId.trim();
        this.status = status == null ? PolicyStatus.PROPOSED : status;
        Map<String, PolicyVote> map = new LinkedHashMap<>();
        if (votes != null) {
            for (PolicyVote v : votes) {
                map.put(v.voterId(), v);
            }
        }
        this.votesByVoter = Collections.unmodifiableMap(map);
        this.resolvedAtEpochMs = resolvedAtEpochMs;
        this.proposedAtEpochMs = proposedAtEpochMs;
    }

    /**
     * Proposes a new policy.
     *
     * @param id policy identifier
     * @param title policy title
     * @param body policy text
     * @param proposerId opaque proposer identifier
     * @param proposedAtEpochMs proposal time in epoch milliseconds
     * @return proposed policy with no votes or resolution
     */
    public static Policy propose(
            String id,
            String title,
            String body,
            String proposerId,
            long proposedAtEpochMs
    ) {
        return new Policy(
                id, title, body, proposerId,
                PolicyStatus.PROPOSED, List.of(), null, proposedAtEpochMs
        );
    }

    /**
     * Returns the policy identifier.
     *
     * @return policy identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the policy title.
     *
     * @return title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the policy body.
     *
     * @return body text
     */
    public String body() {
        return body;
    }

    /**
     * Returns the proposer identifier.
     *
     * @return opaque proposer identifier
     */
    public String proposerId() {
        return proposerId;
    }

    /**
     * Returns the lifecycle status.
     *
     * @return policy status
     */
    public PolicyStatus status() {
        return status;
    }

    /**
     * Returns votes in insertion order.
     *
     * @return immutable vote list
     */
    public List<PolicyVote> votes() {
        return List.copyOf(votesByVoter.values());
    }

    /**
     * Finds a voter's current vote.
     *
     * @param voterId voter identifier
     * @return matching vote, or empty when absent
     */
    public Optional<PolicyVote> voteOf(String voterId) {
        if (voterId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(votesByVoter.get(voterId.trim()));
    }

    /**
     * Returns the resolution time.
     *
     * @return optional resolution epoch milliseconds
     */
    public Optional<Long> resolvedAtEpochMs() {
        return Optional.ofNullable(resolvedAtEpochMs);
    }

    /**
     * Returns the proposal time.
     *
     * @return optional proposal epoch milliseconds
     */
    public Optional<Long> proposedAtEpochMs() {
        return Optional.ofNullable(proposedAtEpochMs);
    }

    /**
     * Counts affirmative votes.
     *
     * @return number of yes votes
     */
    public int yesCount() {
        return count(VoteChoice.YES);
    }

    /**
     * Counts negative votes.
     *
     * @return number of no votes
     */
    public int noCount() {
        return count(VoteChoice.NO);
    }

    /**
     * Counts abstentions.
     *
     * @return number of abstain votes
     */
    public int abstainCount() {
        return count(VoteChoice.ABSTAIN);
    }

    private int count(VoteChoice c) {
        int n = 0;
        for (PolicyVote v : votesByVoter.values()) {
            if (v.choice() == c) {
                n++;
            }
        }
        return n;
    }

    Policy withVote(PolicyVote vote) {
        Objects.requireNonNull(vote, "vote");
        if (status.isTerminal()) {
            throw new IllegalStateException("policy " + id + " is already " + status);
        }
        Map<String, PolicyVote> next = new LinkedHashMap<>(votesByVoter);
        next.put(vote.voterId(), vote);
        return new Policy(
                id, title, body, proposerId, status,
                new ArrayList<>(next.values()), resolvedAtEpochMs, proposedAtEpochMs
        );
    }

    Policy withStatus(PolicyStatus newStatus, long resolvedAt) {
        Objects.requireNonNull(newStatus, "status");
        return new Policy(
                id, title, body, proposerId, newStatus,
                new ArrayList<>(votesByVoter.values()),
                newStatus.isTerminal() ? resolvedAt : resolvedAtEpochMs,
                proposedAtEpochMs
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Policy that)) {
            return false;
        }
        return id.equals(that.id)
                && title.equals(that.title)
                && body.equals(that.body)
                && proposerId.equals(that.proposerId)
                && status == that.status
                && votesByVoter.equals(that.votesByVoter)
                && Objects.equals(resolvedAtEpochMs, that.resolvedAtEpochMs)
                && Objects.equals(proposedAtEpochMs, that.proposedAtEpochMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, body, proposerId, status, votesByVoter,
                resolvedAtEpochMs, proposedAtEpochMs);
    }

    @Override
    public String toString() {
        return "Policy{id='" + id + "', status=" + status + ", votes=" + votesByVoter.size() + '}';
    }
}
