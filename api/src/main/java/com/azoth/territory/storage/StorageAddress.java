package com.azoth.territory.storage;

import java.util.Objects;

/**
 * Address of a single storage slot: a tab within a guild's storage.
 * <p>
 * {@code slotIndex} is zero-based; negative indexes are rejected.
 */
public record StorageAddress(String guildId, String tabId, int slotIndex) {

    public StorageAddress {
        guildId = trimRequired(guildId, "guildId");
        tabId = trimRequired(tabId, "tabId");
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be non-negative, got " + slotIndex);
        }
    }

    private static String trimRequired(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
