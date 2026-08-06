package com.azoth.territory.influence;

/** Outcome of a declare/cancel attempt (spec §7). */
public enum DeclareStatus {
    DECLARED,
    CANCELLED,
    NOT_ELIGIBLE,
    NOT_AT_CAP,
    NOT_AUTHORIZED,
    RACE_ACTIVE,
    TERRITORY_UNKNOWN,
    UNGOVERNABLE,
    STORAGE_ERROR
}
