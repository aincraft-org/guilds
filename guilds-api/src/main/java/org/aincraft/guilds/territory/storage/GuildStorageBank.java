package org.aincraft.guilds.territory.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * Guild item bank metadata persisted in SQL.
 */
public record GuildStorageBank(String guildId, int schemaVersion, Instant createdAt, Instant updatedAt) {
    public GuildStorageBank {
        guildId = requireText(guildId, "guildId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
