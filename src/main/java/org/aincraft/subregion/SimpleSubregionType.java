package org.aincraft.subregion;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable implementation of SubregionType with basic metadata.
 */
public final class SimpleSubregionType implements SubregionType {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final String id;
    private final String displayName;
    private final String description;
    private final boolean showEnterNotification;

    /**
     * Creates a new SimpleSubregionType with notifications enabled.
     *
     * @param id          unique type ID (lowercase alphanumeric with underscores)
     * @param displayName human-readable name
     * @param description description of the type
     * @throws IllegalArgumentException if ID format is invalid
     */
    public SimpleSubregionType(String id, String displayName, String description) {
        this(id, displayName, description, true);
    }

    /**
     * Creates a new SimpleSubregionType.
     *
     * @param id                    unique type ID (lowercase alphanumeric with underscores)
     * @param displayName           human-readable name
     * @param description           description of the type
     * @param showEnterNotification whether to show notifications on entry
     * @throws IllegalArgumentException if ID format is invalid
     */
    public SimpleSubregionType(String id, String displayName, String description, boolean showEnterNotification) {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(displayName, "Display name cannot be null");

        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Type ID must be lowercase alphanumeric with underscores only: " + id);
        }

        this.id = id;
        this.displayName = displayName;
        this.description = description != null ? description : "";
        this.showEnterNotification = showEnterNotification;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean showEnterNotification() {
        return showEnterNotification;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubregionType that)) return false;
        return Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimpleSubregionType{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}
