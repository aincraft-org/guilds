package org.aincraft.guilds.territory.storage;

/**
 * Identifies a guild storage slot within a tab.
 */
public record StorageAddress(String guildId, String tabId, int slotIndex) {
    public StorageAddress {
        guildId = requireText(guildId, "guildId");
        tabId = requireText(tabId, "tabId");
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
