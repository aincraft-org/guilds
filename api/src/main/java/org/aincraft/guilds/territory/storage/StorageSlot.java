package org.aincraft.guilds.territory.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * Occupied storage slot with optimistic version metadata.
 */
public record StorageSlot(
        String guildId,
        String tabId,
        int slotIndex,
        OpaqueItemPayload item,
        long version,
        Instant updatedAt
) {
    @SuppressWarnings("SelfAssignment")
    public StorageSlot {
        guildId = requireText(guildId, "guildId");
        tabId = requireText(tabId, "tabId");
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        item = Objects.requireNonNull(item, "item");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
