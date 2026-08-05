package com.azoth.territory.decree;

import java.util.Locale;
import java.util.Objects;

/**
 * A catalogued trade good with a stable id and category tag (e.g. vegetables).
 */
public final class Good {
    private final String id;
    private final String displayName;
    private final String category;

    public Good(String id, String displayName, String category) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("good id is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("good category is required");
        }
        this.id = normalizeId(id);
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName.trim();
        this.category = normalizeId(category);
    }

    public static String normalizeId(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String category() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Good that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Good{id='" + id + "', category='" + category + "'}";
    }
}
