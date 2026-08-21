package org.aincraft.guilds.storage.persist;

/** Durable guild storage mutation lifecycle. */
public enum StorageOperationStatus {
    PENDING,
    COMMITTED,
    COMPENSATED,
    UNKNOWN
}
