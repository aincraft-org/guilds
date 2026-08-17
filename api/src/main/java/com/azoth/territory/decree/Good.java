package com.azoth.territory.decree;

import java.util.Locale;

/**
 * A catalogued trade good with a stable id and category tag (e.g. vegetables).
 *
 * @param id stable good identifier
 * @param displayName human-readable name
 * @param category category tag
 */
public record Good(String id, String displayName, String category) {

    /** Validates and normalizes a good. */
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

    /**
     * Normalizes a raw good identifier.
     *
     * @param raw identifier to normalize
     * @return normalized identifier
     */
    public static String normalizeId(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Good{id='" + id + "', category='" + category + "'}";
    }
}
