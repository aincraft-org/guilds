package dev.mintychochip.territory.model;

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

    /**
     * Parses a zone type name case-insensitively.
     *
     * @param raw zone type name
     * @return parsed zone type
     * @throws IllegalArgumentException if the input is blank or unknown
     */
    public static ZoneType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("zone type is required");
        }
        return ZoneType.valueOf(raw.trim().toUpperCase());
    }
}
