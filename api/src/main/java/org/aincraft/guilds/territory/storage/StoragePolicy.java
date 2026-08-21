package org.aincraft.guilds.territory.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * Role thresholds for guild storage operations.
 */
public record StoragePolicy(
        String guildId,
        String depositRole,
        String withdrawRole,
        String manageRole,
        Instant updatedAt
) {
    public StoragePolicy {
        guildId = requireText(guildId, "guildId");
        depositRole = requireText(depositRole, "depositRole");
        withdrawRole = requireText(withdrawRole, "withdrawRole");
        manageRole = requireText(manageRole, "manageRole");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
