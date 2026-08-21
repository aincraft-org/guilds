package org.aincraft.guilds.storage.persist;

/** Lifecycle of a failed deposit awaiting player item restoration. */
public enum StorageDepositRestorationStatus {
    PENDING,
    RESTORING,
    RESTORED
}
