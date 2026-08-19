package org.aincraft.guilds.territory.decree;

import java.util.Locale;

/**
 * A catalogued trade good with a stable id and category tag (e.g. vegetables).
 */
public record Good(String id, String displayName, String category) {

    public Good {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("good id is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("good category is required");
        }
        id = normalizeId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        category = normalizeId(category);
    }

    public static String normalizeId(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
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
