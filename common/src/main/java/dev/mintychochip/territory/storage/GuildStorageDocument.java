package dev.mintychochip.territory.storage;

import java.util.List;
import java.util.Objects;

/**
 * Persisted bank contents, independent of the facility used to open them.
 *
 * @param guildId owning guild
 * @param capacitySlots logical slot count
 * @param revision optimistic concurrency token
 * @param slots occupied slots
 */
public record GuildStorageDocument(
        String guildId,
        int capacitySlots,
        int revision,
        List<StorageSlot> slots
) {
    /**
     * Validates and copies the document.
     *
     * @throws IllegalArgumentException if identifiers are blank or capacity is not positive
     */
    public GuildStorageDocument {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException("guildId is required");
        }
        guildId = guildId.trim();
        if (capacitySlots <= 0) {
            throw new IllegalArgumentException("capacitySlots must be positive");
        }
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    /**
     * Creates an empty bank at the default capacity.
     *
     * @param guildId owning guild
     * @return empty document at revision 0
     */
    public static GuildStorageDocument empty(String guildId) {
        return new GuildStorageDocument(guildId, StorageSnapshot.DEFAULT_CAPACITY, 0, List.of());
    }
}
