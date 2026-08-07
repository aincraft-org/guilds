package com.azoth.territory.storage;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of opening a guild's storage, carrying the current snapshot on success.
 * <p>
 * The snapshot is present only when {@code status} is {@link StorageStatus#SUCCESS};
 * a non-success result must carry an empty {@code snapshot}.
 */
public record StorageOpenResult(
        StorageStatus status,
        String message,
        Optional<GuildStorageSnapshot> snapshot
) {

    public StorageOpenResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.isPresent() && status != StorageStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "snapshot is only allowed on SUCCESS, got " + status);
        }
    }
}
