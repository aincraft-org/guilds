package com.azoth.territory.model;

import java.util.Objects;

/**
 * Named subregion inside a territory with a zone type and boundary.
 */
public final class Zone {
    private final String id;
    private final String name;
    private final ZoneType type;
    private final Boundary boundary;
    /**
     * Higher priority wins when multiple zones contain the same point.
     * Default 0; Claimable overlays typically use higher values.
     */
    private final int priority;

    public Zone(String id, String name, ZoneType type, Boundary boundary) {
        this(id, name, type, boundary, 0);
    }

    public Zone(String id, String name, ZoneType type, Boundary boundary, int priority) {
        this.id = requireId(id);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
        this.type = Objects.requireNonNull(type, "type");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        if (boundary.isEmpty()) {
            throw new IllegalArgumentException("zone boundary must not be empty: " + this.id);
        }
        this.priority = priority;
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("zone id is required");
        }
        return id.trim();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ZoneType type() {
        return type;
    }

    public Boundary boundary() {
        return boundary;
    }

    public int priority() {
        return priority;
    }

    public boolean contains(int blockX, int blockZ) {
        return boundary.contains(blockX, blockZ);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Zone that)) {
            return false;
        }
        return priority == that.priority
                && id.equals(that.id)
                && name.equals(that.name)
                && type == that.type
                && boundary.equals(that.boundary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, boundary, priority);
    }

    @Override
    public String toString() {
        return "Zone{id='" + id + "', type=" + type + ", priority=" + priority + '}';
    }
}
