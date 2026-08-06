package com.azoth.territory.model;

/**
 * Classification of a subregion inside a territory.
 * <p>
 * Enforcement (protection, claims, PvP) is out of scope for this plugin —
 * zone types exist so later systems can key off them.
 */
public enum ZoneType {
    /**
     * Open / non-claimable land inside a territory (default when no other zone matches).
     */
    WILDERNESS,
    /**
     * Land eligible for future guild/claim systems; classification only here.
     */
    CLAIMABLE;

    public static ZoneType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("zone type is required");
        }
        return ZoneType.valueOf(raw.trim().toUpperCase());
    }
}
