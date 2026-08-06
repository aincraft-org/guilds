package com.azoth.territory.model;

import java.util.Objects;

/**
 * A named, settlement-owned location hook. Inventories, listings, and access
 * policy remain owned by the integrating storage or shop plugin.
 */
public record SettlementFacility(
        String id,
        String name,
        String territoryId,
        FacilityType type,
        String worldId,
        int x,
        int y,
        int z
) {
    public SettlementFacility {
        id = requireText(id, "id");
        name = name == null || name.isBlank() ? id : name.trim();
        territoryId = requireText(territoryId, "territoryId");
        type = Objects.requireNonNull(type, "type");
        worldId = requireText(worldId, "worldId");
    }

    public boolean isAt(String worldId, int x, int y, int z) {
        return this.worldId.equals(worldId == null ? null : worldId.trim())
                && this.x == x && this.y == y && this.z == z;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
