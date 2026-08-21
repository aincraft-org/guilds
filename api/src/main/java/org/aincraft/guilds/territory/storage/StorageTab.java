package org.aincraft.guilds.territory.storage;

/**
 * Named storage tab within a guild bank.
 */
public record StorageTab(
        String guildId,
        String tabId,
        String displayName,
        int ordinal,
        int capacitySlots,
        boolean unlocked
) {
    public StorageTab {
        guildId = requireText(guildId, "guildId");
        tabId = requireText(tabId, "tabId");
        displayName = requireText(displayName, "displayName");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
        if (capacitySlots < 1) {
            throw new IllegalArgumentException("capacitySlots must be >= 1");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
