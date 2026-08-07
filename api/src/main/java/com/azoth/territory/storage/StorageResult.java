package com.azoth.territory.storage;

import java.util.Objects;

/** Immutable mutation outcome for a guild storage operation. */
public record StorageResult(StorageStatus status, String message) {

    public StorageResult {
        Objects.requireNonNull(status, "status");
    }
}
