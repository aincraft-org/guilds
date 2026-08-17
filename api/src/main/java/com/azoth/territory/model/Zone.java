package com.azoth.territory.model;

import java.util.Objects;

/**
 * Named subregion inside a territory with a zone type and boundary.
 */
public record Zone(String id, String name, ZoneType type, Boundary boundary, int priority) {

    public Zone(String id, String name, ZoneType type, Boundary boundary) {
        this(id, name, type, boundary, 0);
    }

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

    public boolean contains(int blockX, int blockZ) {
        return boundary.contains(blockX, blockZ);
    }

    @Override
    public String toString() {
        return "Zone{id='" + id + "', type=" + type + ", priority=" + priority + '}';
    }
}
