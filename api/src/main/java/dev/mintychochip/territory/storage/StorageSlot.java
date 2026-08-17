package dev.mintychochip.territory.storage;

import java.util.Objects;

/**
 * One occupied logical slot in a guild bank.
 *
 * @param index zero-based slot index
 * @param item opaque item payload
 */
public record StorageSlot(int index, OpaqueItemPayload item) {
    /**
     * Validates the slot.
     *
     * @throws IllegalArgumentException if {@code index} is negative
     * @throws NullPointerException if {@code item} is {@code null}
     */
    @SuppressWarnings("SelfAssignment")
    public StorageSlot {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        item = Objects.requireNonNull(item, "item");
    }
}
