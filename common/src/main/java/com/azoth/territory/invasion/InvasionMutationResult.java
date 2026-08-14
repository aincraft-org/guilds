package com.azoth.territory.invasion;

/** Outcome of a persistence-backed entity registration mutation. */
public enum InvasionMutationResult {
    NO_CHANGE,
    PERSISTED,
    PERSISTENCE_FAILED
}
