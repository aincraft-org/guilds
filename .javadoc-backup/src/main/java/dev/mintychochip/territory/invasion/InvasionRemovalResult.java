package dev.mintychochip.territory.invasion;

import java.util.List;
import java.util.Objects;

/** Persistence outcome and ordered lifecycle transitions from one mob removal. */
public record InvasionRemovalResult(InvasionMutationResult mutation, List<InvasionTransition> transitions) {
    public InvasionRemovalResult {
        Objects.requireNonNull(mutation, "mutation");
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
    }

    public static InvasionRemovalResult noChange() {
        return new InvasionRemovalResult(InvasionMutationResult.NO_CHANGE, List.of(InvasionTransition.NO_CHANGE));
    }

    public static InvasionRemovalResult failed() {
        return new InvasionRemovalResult(InvasionMutationResult.PERSISTENCE_FAILED, List.of(InvasionTransition.NO_CHANGE));
    }
}
