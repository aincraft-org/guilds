package com.azoth.territory.storage;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of withdrawing an item from guild storage, carrying the payload on success.
 * <p>
 * The payload is present only when {@code status} is {@link StorageStatus#SUCCESS};
 * a non-success result must carry an empty {@code payload}.
 */
public record StorageWithdrawResult(
        StorageStatus status,
        String message,
        Optional<OpaqueItemPayload> payload
) {

    public StorageWithdrawResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(payload, "payload");
        if (payload.isPresent() && status != StorageStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "payload is only allowed on SUCCESS, got " + status);
        }
    }
}
