package dev.mintychochip.territory.model;

import java.util.Objects;

/**
 * A named, settlement-owned location hook. Inventories, listings, and access
 * policy remain owned by the integrating storage or shop plugin.
 *
 * @param id facility identifier
 * @param name display name
 * @param territoryId owning territory identifier
 * @param type facility category
 * @param worldId world identifier
 * @param x block X coordinate
 * @param y block Y coordinate
 * @param z block Z coordinate
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
    /** Validates and normalizes this facility.
     * @throws IllegalArgumentException if a required text value is blank
     * @throws NullPointerException if {@code type} is {@code null}
     */
    @SuppressWarnings("SelfAssignment")
    public SettlementFacility {
        id = requireText(id, "id");
        name = name == null || name.isBlank() ? id : name.trim();
        territoryId = requireText(territoryId, "territoryId");
        type = Objects.requireNonNull(type, "type");
        worldId = requireText(worldId, "worldId");
    }

    /** Determines whether this facility occupies a given world position.
     * @param worldId candidate world identifier
     * @param x candidate block X coordinate
     * @param y candidate block Y coordinate
     * @param z candidate block Z coordinate
     * @return {@code true} when all coordinates identify this facility
     */
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
