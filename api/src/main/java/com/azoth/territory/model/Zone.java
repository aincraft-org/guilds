package com.azoth.territory.model;

import java.util.Objects;

/**
 * Named subregion inside a territory with a zone type and boundary.
 *
 * @param id zone identifier
 * @param name display name
 * @param type zone classification
 * @param boundary spatial boundary
 * @param priority precedence among matching zones
 */
public record Zone(String id, String name, ZoneType type, Boundary boundary, int priority) {

    /** Creates a zone with default priority.
     * @param id zone identifier
     * @param name display name
     * @param type zone classification
     * @param boundary spatial boundary
     */
    public Zone(String id, String name, ZoneType type, Boundary boundary) {
        this(id, name, type, boundary, 0);
    }

    /** Validates and normalizes this zone.
     * @throws IllegalArgumentException if the identifier is blank or boundary is empty
     * @throws NullPointerException if {@code type} or {@code boundary} is {@code null}
     */
    public Zone {
        id = requireId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(boundary, "boundary");
        if (boundary.isEmpty()) {
            throw new IllegalArgumentException("zone boundary must not be empty: " + id);
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("zone id is required");
        }
        return id.trim();
    }

    /** Determines whether a block is in this zone.
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return whether the block is contained
     */
    public boolean contains(int blockX, int blockZ) {
        return boundary.contains(blockX, blockZ);
    }

    /** Returns a concise textual representation.
     * @return a description of this zone
     */
    @Override
    public String toString() {
        return "Zone{id='" + id + "', type=" + type + ", priority=" + priority + '}';
    }
}
