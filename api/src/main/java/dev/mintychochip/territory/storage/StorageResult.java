package dev.mintychochip.territory.storage;

import java.util.Objects;

/**
 * Result of a storage command.
 *
 * @param status operation outcome
 * @param snapshot bank view when the operation produced one
 * @param item withdrawn payload when applicable
 */
public record StorageResult(StorageStatus status, StorageSnapshot snapshot, OpaqueItemPayload item) {
    /**
     * Requires a status.
     *
     * @throws NullPointerException if {@code status} is {@code null}
     */
    @SuppressWarnings("SelfAssignment")
    public StorageResult {
        status = Objects.requireNonNull(status, "status");
    }

    /**
     * Creates a denial with no snapshot or item.
     *
     * @param status denial status
     * @return result
     */
    public static StorageResult denied(StorageStatus status) {
        return new StorageResult(status, null, null);
    }

    /**
     * Returns whether the operation completed a mutation or view.
     *
     * @return {@code true} for opened, deposited, withdrawn, saved, or closed
     */
    public boolean succeeded() {
        return status == StorageStatus.OPENED
                || status == StorageStatus.DEPOSITED
                || status == StorageStatus.WITHDRAWN
                || status == StorageStatus.SAVED
                || status == StorageStatus.CLOSED;
    }
}
