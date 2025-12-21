package org.aincraft.role;

import org.aincraft.GuildPermission;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing a default role template from config.
 * Used by the Flyweight pattern to generate guild-specific role instances.
 */
public final class DefaultRoleTemplate {
    private final String name;
    private final int permissions;
    private final int priority;
    private final boolean autoAssign;

    public DefaultRoleTemplate(String name, int permissions, int priority, boolean autoAssign) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.permissions = permissions;
        this.priority = priority;
        this.autoAssign = autoAssign;
    }

    /**
     * Parse a DefaultRoleTemplate from a YAML ConfigurationSection.
     *
     * @param section the configuration section containing role definition
     * @return parsed DefaultRoleTemplate
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public static DefaultRoleTemplate fromConfig(ConfigurationSection section) {
        String name = section.getString("name");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Role name is required");
        }

        int priority = section.getInt("priority", 0);
        boolean autoAssign = section.getBoolean("auto-assign", false);

        List<String> permissionNames = section.getStringList("permissions");
        int permissions = parsePermissions(permissionNames);

        return new DefaultRoleTemplate(name, permissions, priority, autoAssign);
    }

    /**
     * Convert permission name strings to bitfield.
     *
     * @param permissionNames list of permission names (e.g., ["BUILD", "DESTROY"])
     * @return bitfield with all specified permissions OR'd together
     */
    static int parsePermissions(List<String> permissionNames) {
        int result = 0;
        if (permissionNames == null || permissionNames.isEmpty()) {
            return result;
        }

        for (String permName : permissionNames) {
            if (permName == null || permName.trim().isEmpty()) {
                continue;
            }
            try {
                GuildPermission perm = GuildPermission.valueOf(permName.trim().toUpperCase());
                result |= perm.getBit();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid permission name: '" + permName + "'. Valid permissions: " + java.util.Arrays.toString(GuildPermission.values()), e);
            }
        }
        return result;
    }

    public String getName() {
        return name;
    }

    public int getPermissions() {
        return permissions;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAutoAssign() {
        return autoAssign;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultRoleTemplate that = (DefaultRoleTemplate) o;
        return permissions == that.permissions &&
                priority == that.priority &&
                autoAssign == that.autoAssign &&
                name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, permissions, priority, autoAssign);
    }

    @Override
    public String toString() {
        return "DefaultRoleTemplate{" +
                "name='" + name + '\'' +
                ", permissions=" + permissions +
                ", priority=" + priority +
                ", autoAssign=" + autoAssign +
                '}';
    }
}
