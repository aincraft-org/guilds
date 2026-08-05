package com.azoth.territory.model;

import com.azoth.territory.decree.DecreeEffects;

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
    private final DecreeEffects effects;

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
        this(id, title, body, proposerId, status, votes, resolvedAtEpochMs, proposedAtEpochMs, DecreeEffects.empty());
    }

    public Policy(
            String id,
            String title,
            String body,
            String proposerId,
            PolicyStatus status,
            List<PolicyVote> votes,
            Long resolvedAtEpochMs,
            Long proposedAtEpochMs,
            DecreeEffects effects
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
        this.effects = effects == null ? DecreeEffects.empty() : effects;
    }

    public static Policy propose(
            String id,
            String title,
            String body,
            String proposerId,
            long proposedAtEpochMs
    ) {
        return new Policy(
                id, title, body, proposerId,
                PolicyStatus.PROPOSED, List.of(), null, proposedAtEpochMs, DecreeEffects.empty()
        );
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public String proposerId() {
        return proposerId;
    }

    public PolicyStatus status() {
        return status;
    }

    public List<PolicyVote> votes() {
        return List.copyOf(votesByVoter.values());
    }

    public Optional<PolicyVote> voteOf(String voterId) {
        if (voterId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(votesByVoter.get(voterId.trim()));
    }

    public Optional<Long> resolvedAtEpochMs() {
        return Optional.ofNullable(resolvedAtEpochMs);
    }

    public Optional<Long> proposedAtEpochMs() {
        return Optional.ofNullable(proposedAtEpochMs);
    }

    public DecreeEffects effects() {
        return effects;
    }

    public int yesCount() {
        return count(VoteChoice.YES);
    }

    public int noCount() {
        return count(VoteChoice.NO);
    }

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
                new ArrayList<>(next.values()), resolvedAtEpochMs, proposedAtEpochMs, effects
        );
    }

    Policy withStatus(PolicyStatus newStatus, long resolvedAt) {
        Objects.requireNonNull(newStatus, "status");
        return new Policy(
                id, title, body, proposerId, newStatus,
                new ArrayList<>(votesByVoter.values()),
                newStatus.isTerminal() ? resolvedAt : resolvedAtEpochMs,
                proposedAtEpochMs,
                effects
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
                && Objects.equals(proposedAtEpochMs, that.proposedAtEpochMs)
                && effects.equals(that.effects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, body, proposerId, status, votesByVoter,
                resolvedAtEpochMs, proposedAtEpochMs, effects);
    }

    @Override
    public String toString() {
        return "Policy{id='" + id + "', status=" + status + ", votes=" + votesByVoter.size() + '}';
    }
}
