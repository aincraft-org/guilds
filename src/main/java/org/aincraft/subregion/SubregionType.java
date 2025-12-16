package org.aincraft.subregion;

/**
 * Represents a type of subregion with metadata.
 * Types are labels for categorization and do not control validation or permissions.
 */
public interface SubregionType {

    /**
     * Gets the unique identifier for this type.
     * Must be lowercase alphanumeric with underscores only.
     *
     * @return the type ID
     */
    String getId();

    /**
     * Gets the human-readable display name.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Gets an optional description of what this type is used for.
     *
     * @return the description
     */
    String getDescription();

    /**
     * Whether to show a notification when a player enters a region of this type.
     *
     * @return true if notifications should be shown
     */
    default boolean showEnterNotification() {
        return true;
    }
}
