package com.azoth.territory.model;

import com.azoth.territory.decree.DecreeEffects;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Form-specific eligibility and passage rules for territory policies.
 * <p>
 * Pure domain — no Bukkit. Opaque proposer/voter ids only.
 */
public final class PolicyRules {
    private PolicyRules() {
    }

    /**
     * Holders of the form's authority seats (filled only). These may propose and vote/decree.
     */
    public static Set<String> electorate(Government government) {
        Objects.requireNonNull(government, "government");
        if (!government.isAssigned()) {
            return Set.of();
        }
        SeatRole role = government.form().authorityRole();
        Set<String> ids = new LinkedHashSet<>();
        for (GovernmentSeat s : government.seatsByRole(role)) {
            s.holderId().ifPresent(ids::add);
        }
        return ids;
    }

    public static boolean canPropose(Government government, String proposerId) {
        if (government == null || !government.isAssigned()) {
            return false;
        }
        if (proposerId == null || proposerId.isBlank()) {
            return false;
        }
        return electorate(government).contains(proposerId.trim());
    }

    public static boolean canVote(Government government, String voterId) {
        if (government == null || !government.isAssigned()) {
            return false;
        }
        if (government.form().decisionStyle() != GovernmentForm.DecisionStyle.MAJORITY_SEATS) {
            return false;
        }
        if (voterId == null || voterId.isBlank()) {
            return false;
        }
        return electorate(government).contains(voterId.trim());
    }

    public static boolean canDecree(Government government, String authorityId) {
        if (government == null || !government.isAssigned()) {
            return false;
        }
        if (government.form().decisionStyle() != GovernmentForm.DecisionStyle.DECREE) {
            return false;
        }
        if (authorityId == null || authorityId.isBlank()) {
            return false;
        }
        return electorate(government).contains(authorityId.trim());
    }

    /**
     * Create a proposed policy under the government. Proposer must be in the electorate.
     */
    /**
     * Create a proposed policy under the government (no structured effects).
     * Proposer must be in the electorate.
     */
    public static Policy propose(
            Government government,
            String id,
            String title,
            String body,
            String proposerId,
            long nowEpochMs
    ) {
        return propose(government, id, title, body, proposerId, nowEpochMs, DecreeEffects.empty());
    }

    /**
     * Create a proposed policy under the government with structured decree effects.
     * Proposer must be in the electorate.
     */
    public static Policy propose(
            Government government,
            String id,
            String title,
            String body,
            String proposerId,
            long nowEpochMs,
            DecreeEffects effects
    ) {
        Objects.requireNonNull(government, "government");
        if (!government.isAssigned()) {
            throw new IllegalArgumentException("cannot propose policy without an assigned government");
        }
        if (!canPropose(government, proposerId)) {
            throw new IllegalArgumentException(
                    "proposer '" + proposerId + "' is not eligible under " + government.form()
            );
        }
        Policy p = Policy.propose(id, title, body, proposerId, nowEpochMs);
        if (effects == null || effects.isEmpty()) {
            return p;
        }
        return new Policy(
                p.id(), p.title(), p.body(), p.proposerId(), PolicyStatus.PROPOSED,
                p.votes(), null, nowEpochMs, effects
        );
    }

    /**
     * Cast or replace a vote (majority-style forms only). May auto-resolve.
     */
    public static Policy castVote(
            Government government,
            Policy policy,
            String voterId,
            VoteChoice choice,
            long nowEpochMs
    ) {
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(choice, "choice");
        if (policy.status().isTerminal()) {
            throw new IllegalStateException("policy " + policy.id() + " is already " + policy.status());
        }
        if (!canVote(government, voterId)) {
            throw new IllegalArgumentException(
                    "voter '" + voterId + "' is not eligible under " + government.form()
                            + " (decision style " + government.form().decisionStyle() + ")"
            );
        }
        Policy next = policy.withVote(new PolicyVote(voterId.trim(), choice, nowEpochMs));
        return tryAutoResolveMajority(government, next, nowEpochMs);
    }

    /**
     * Decree pass/reject (monarchy). Records authority as a YES/NO vote.
     */
    public static Policy decree(
            Government government,
            Policy policy,
            String authorityId,
            boolean pass,
            long nowEpochMs
    ) {
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(policy, "policy");
        if (policy.status().isTerminal()) {
            throw new IllegalStateException("policy " + policy.id() + " is already " + policy.status());
        }
        if (!canDecree(government, authorityId)) {
            throw new IllegalArgumentException(
                    "authority '" + authorityId + "' cannot decree under " + government.form()
            );
        }
        VoteChoice choice = pass ? VoteChoice.YES : VoteChoice.NO;
        Policy next = policy.withVote(new PolicyVote(authorityId.trim(), choice, nowEpochMs));
        return next.withStatus(pass ? PolicyStatus.PASSED : PolicyStatus.REJECTED, nowEpochMs);
    }

    /**
     * Force a majority tally (useful after external vote imports). No-op if not majority form.
     */
    public static Policy resolveIfPossible(Government government, Policy policy, long nowEpochMs) {
        Objects.requireNonNull(government, "government");
        Objects.requireNonNull(policy, "policy");
        if (policy.status().isTerminal()) {
            return policy;
        }
        if (government.form().decisionStyle() != GovernmentForm.DecisionStyle.MAJORITY_SEATS) {
            throw new IllegalArgumentException(
                    "cannot majority-resolve under " + government.form() + "; use decree"
            );
        }
        return tryAutoResolveMajority(government, policy, nowEpochMs);
    }

    private static Policy tryAutoResolveMajority(Government government, Policy policy, long now) {
        Set<String> eligible = electorate(government);
        if (eligible.isEmpty()) {
            return policy;
        }
        int filled = eligible.size();
        int yes = 0;
        int no = 0;
        int voted = 0;
        for (String id : eligible) {
            var v = policy.voteOf(id);
            if (v.isEmpty()) {
                continue;
            }
            voted++;
            switch (v.get().choice()) {
                case YES -> yes++;
                case NO -> no++;
                case ABSTAIN -> {
                }
            }
        }
        int majorityThreshold = filled / 2; // need strictly more than half
        if (yes > majorityThreshold) {
            return policy.withStatus(PolicyStatus.PASSED, now);
        }
        if (no > majorityThreshold) {
            return policy.withStatus(PolicyStatus.REJECTED, now);
        }
        // All eligible have cast a ballot and no strict majority of yes → reject
        if (voted >= filled && yes <= majorityThreshold) {
            return policy.withStatus(PolicyStatus.REJECTED, now);
        }
        return policy;
    }

    /**
     * Human-readable summary of decision path for a form (for docs/tests).
     */
    public static String describeDecisionPath(GovernmentForm form) {
        Objects.requireNonNull(form, "form");
        return switch (form) {
            case ANARCHY -> "anarchy; cannot adopt policies";
            case MONARCHY -> "decree by SOVEREIGN seat holder";
            case OLIGARCHY -> "majority YES among filled COUNCILOR seats";
            case DEMOCRACY -> "majority YES among filled REPRESENTATIVE seats";
        };
    }
}
