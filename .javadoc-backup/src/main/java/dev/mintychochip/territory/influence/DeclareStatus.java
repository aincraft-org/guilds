package dev.mintychochip.territory.influence;

/** Outcome of a declare/cancel attempt (spec §7). */
public enum DeclareStatus {
    /** Declaration succeeded. */
    DECLARED,
    /** Declaration cancellation succeeded. */
    CANCELLED,
    /** The guild is not eligible. */
    NOT_ELIGIBLE,
    /** The guild has not reached the required cap. */
    NOT_AT_CAP,
    /** The requester is not authorized. */
    NOT_AUTHORIZED,
    /** A race is already active. */
    RACE_ACTIVE,
    /** The territory is unknown. */
    TERRITORY_UNKNOWN,
    /** The territory cannot be governed. */
    UNGOVERNABLE,
    /** Storage failed. */
    STORAGE_ERROR
}
