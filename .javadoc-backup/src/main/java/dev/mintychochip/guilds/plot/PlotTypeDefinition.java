package dev.mintychochip.guilds.plot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rich metadata model for extensible plot types
 * Supports custom properties and plugin-specific configuration
 */
public class PlotTypeDefinition {

    /** The type name. */
    private final String typeName;
    /** The display name. */
    private final String displayName;
    /** The description. */
    private final String description;
    /** The plugin name. */
    private final String pluginName; // null for built-in types
    /** The metadata. */
    private final Map<String, Object> metadata; // extensible properties
    /** The permissions. */
    private final Set<String> permissions; // required permissions
    /** The is enabled. */
    private final boolean isEnabled;

    /**
     * Creates a new plot type definition instance.
     * @param builder the builder
     */
    private PlotTypeDefinition(Builder builder) {
        this.typeName = builder.typeName;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.pluginName = builder.pluginName;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.permissions = Collections.unmodifiableSet(builder.permissions);
        this.isEnabled = builder.isEnabled;
    }

    /**
     * Get the unique type identifier
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Get the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description of what this plot type does
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the name of the plugin that registered this type
     * Returns null for built-in types
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * Check if this is a built-in plot type
     */
    public boolean isBuiltIn() {
        return pluginName == null;
    }

    /**
     * Get metadata value by key
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type, T defaultValue) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * Get all metadata as unmodifiable map
     */
    public Map<String, Object> getAllMetadata() {
        return metadata;
    }

    /**
     * Check if a specific metadata key exists
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Get required permissions for this plot type
     */
    public Set<String> getRequiredPermissions() {
        return permissions;
    }

    /**
     * Check if a specific permission is required
     */
    public boolean requiresPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Check if this plot type is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "PlotTypeDefinition{" +
                "typeName='" + typeName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", pluginName='" + pluginName + '\'' +
                ", enabled=" + isEnabled +
                '}';
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlotTypeDefinition that = (PlotTypeDefinition) obj;
        return typeName.equals(that.typeName);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return typeName.hashCode();
    }

    /**
     * Builder pattern for creating plot type definitions
     * Provides fluent API for easy plugin integration
     */
    public static class Builder {
        /** The type name. */
        private String typeName;
        /** The display name. */
        private String displayName;
        /** The description. */
        private String description = "";
        /** The plugin name. */
        private String pluginName;
        /** The metadata. */
        private final Map<String, Object> metadata = new HashMap<>();
        /** The permissions. */
        private final Set<String> permissions = new java.util.HashSet<>();
        /** The is enabled. */
        private boolean isEnabled = true;

        /**
         * Set the unique type identifier (required)
         */
        public Builder typeName(String name) {
            this.typeName = name;
            return this;
        }

        /**
         * Set the human-readable display name (required)
         */
        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        /**
         * Set the description
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the plugin name (for built-in types, leave null)
         */
        public Builder pluginName(String pluginName) {
            this.pluginName = pluginName;
            return this;
        }

        /**
         * Add custom metadata property
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Add multiple metadata properties
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Add a required permission
         */
        public Builder requirePermission(String permission) {
            this.permissions.add(permission);
            return this;
        }

        /**
         * Add multiple required permissions
         */
        public Builder requirePermissions(Set<String> permissions) {
            this.permissions.addAll(permissions);
            return this;
        }

        /**
         * Set whether this plot type is enabled
         */
        public Builder enabled(boolean enabled) {
            this.isEnabled = enabled;
            return this;
        }

        /**
         * Disable this plot type
         */
        public Builder disabled() {
            this.isEnabled = false;
            return this;
        }

        /**
         * Build the plot type definition
         */
        public PlotTypeDefinition build() {
            if (typeName == null || typeName.trim().isEmpty()) {
                throw new IllegalArgumentException("Type name is required");
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                throw new IllegalArgumentException("Display name is required");
            }
            // Normalize type name
            this.typeName = typeName.toLowerCase().replace(" ", "_");
            return new PlotTypeDefinition(this);
        }
    }

    /**
     * Create a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a copy builder from existing definition
     */
    public Builder toBuilder() {
        return new Builder()
                .typeName(this.typeName)
                .displayName(this.displayName)
                .description(this.description)
                .pluginName(this.pluginName)
                .metadata(this.metadata)
                .requirePermissions(this.permissions)
                .enabled(this.isEnabled);
    }
}