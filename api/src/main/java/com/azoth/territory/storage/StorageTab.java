package com.azoth.territory.storage;

import java.util.Objects;

/**
 * One named tab of a guild's storage with a fixed, positive slot capacity.
 * <p>
 * {@code ordinal} orders tabs within the guild; {@code capacitySlots} must be
 * positive. Tab ids are unique within a guild (enforced by
 * {@link GuildStorageSnapshot}).
 */
public record StorageTab(
        String id,
        String displayName,
        int ordinal,
        int capacitySlots,
        boolean unlocked
) {

    public StorageTab {
        id = trimRequired(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (capacitySlots <= 0) {
            throw new IllegalArgumentException(
                    "capacitySlots must be positive, got " + capacitySlots);
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
