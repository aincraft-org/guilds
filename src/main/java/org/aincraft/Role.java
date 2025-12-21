package org.aincraft;

import java.util.UUID;

/**
 * Represents a role with associated permissions.
 * Can be scoped to either a guild or region, with priority-based authority hierarchy.
 */
public final class Role extends AbstractRole<UUID> {
    public static final String DEFAULT_ROLE_NAME = "Member";

    /**
     * Creates a new Role.
     *
     * @param scopeId the guild or region this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority (higher = more authority)
     */
    public Role(UUID scopeId, String name, int permissions, int priority) {
        this(scopeId, name, permissions, priority, null);
    }

    /**
     * Creates a new Role with creation metadata.
     *
     * @param scopeId the guild or region this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority (higher = more authority)
     * @param createdBy the UUID of the player who created this role
     */
    public Role(UUID scopeId, String name, int permissions, int priority, UUID createdBy) {
        super(
            UUID.randomUUID().toString(),
            scopeId,
            name,
            permissions,
            priority,
            createdBy,
            createdBy != null ? System.currentTimeMillis() : null
        );
    }

    /**
     * Creates a new Role with default priority (0).
     *
     * @param scopeId the guild or region this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     */
    public Role(UUID scopeId, String name, int permissions) {
        this(scopeId, name, permissions, 0);
    }

    /**
     * Full constructor for database restoration with creation metadata.
     *
     * @param id the role ID
     * @param scopeId the guild or region this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority
     * @param createdBy the UUID of the creator (nullable for legacy roles)
     * @param createdAt the creation timestamp (nullable for legacy roles)
     */
    public Role(String id, UUID scopeId, String name, int permissions, int priority,
                UUID createdBy, Long createdAt) {
        super(id, scopeId, name, permissions, priority, createdBy, createdAt);
    }

    /**
     * Creates the default "Member" role with basic permissions.
     */
    public static Role createDefault(UUID scopeId) {
        return new Role(scopeId, DEFAULT_ROLE_NAME, GuildPermission.defaultPermissions());
    }

    /**
     * Gets the scope ID (guild or region) this role belongs to.
     */
    public UUID getScopeId() {
        return super.getScopeId();
    }

    /**
     * Checks if this role has higher priority than another role.
     */
    public boolean hasHigherPriorityThan(Role other) {
        return this.priority > other.priority;
    }

    @Override
    public String toString() {
        return "Role{" +
                "id='" + getId() + '\'' +
                ", scopeId='" + getScopeId() + '\'' +
                ", name='" + getName() + '\'' +
                ", permissions=" + getPermissions() +
                ", priority=" + priority +
                '}';
    }
}

