package com.azoth.territory.storage;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable view of a guild's storage: its tabs, the slots currently occupied
 * by opaque payloads, and the access policy.
 * <p>
 * Input collections are copied defensively; the exposed collections are
 * unmodifiable. Tab ids must be unique within the snapshot.
 */
public record GuildStorageSnapshot(
        String guildId,
        List<StorageTab> tabs,
        Map<StorageAddress, OpaqueItemPayload> occupiedSlots,
        GuildStoragePolicy policy
) {

    public GuildStorageSnapshot {
        guildId = trimRequired(guildId, "guildId");
        tabs = List.copyOf(tabs);
        occupiedSlots = Map.copyOf(occupiedSlots);
        Objects.requireNonNull(policy, "policy");
        Set<String> ids = new HashSet<>();
        for (StorageTab tab : tabs) {
            if (!ids.add(tab.id())) {
                throw new IllegalArgumentException("duplicate tab id: " + tab.id());
            }
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
