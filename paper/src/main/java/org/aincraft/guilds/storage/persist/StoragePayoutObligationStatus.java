package org.aincraft.guilds.storage.persist;

/** Lifecycle of a committed withdraw awaiting player payout or storage reinsertion. */
public enum StoragePayoutObligationStatus {
    PENDING,
    DELIVERED,
    REINSERTED
}
