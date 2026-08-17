package dev.mintychochip.territory.storage;

/** Outcome of a guild item-storage operation. */
public enum StorageStatus {
    /** Bank view opened. */
    OPENED,
    /** One slot was credited. */
    DEPOSITED,
    /** One slot was removed. */
    WITHDRAWN,
    /** The full snapshot was persisted. */
    SAVED,
    /** The viewer released the bank session. */
    CLOSED,
    /** No storage facility exists at the location. */
    DENIED_NO_FACILITY,
    /** The facility is not a storage anchor. */
    DENIED_WRONG_TYPE,
    /** The territory has no governing guild. */
    DENIED_NO_GOVERNMENT,
    /** The actor is not a resident of the governing guild. */
    DENIED_NOT_RESIDENT,
    /** The actor lacks deposit or withdraw authority. */
    DENIED_NO_PERMISSION,
    /** Another member already has the bank open. */
    DENIED_IN_USE,
    /** The deposit would exceed capacity. */
    DENIED_CAPACITY,
    /** The requested slot is empty. */
    DENIED_EMPTY_SLOT,
    /** A concurrent write used a newer revision. */
    CONFLICT,
    /** Persistence failed and no mutation was kept. */
    UNAVAILABLE
}
