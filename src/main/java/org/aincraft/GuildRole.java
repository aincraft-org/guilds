package org.aincraft;

import java.util.UUID;

/**
 * Represents a role within a guild with associated permissions.
 * Roles are guild-specific and identified by name + permission bitfield.
 */
public final class GuildRole extends AbstractRole<UUID> {
    public static final String DEFAULT_ROLE_NAME = "Member";

    private int priority;

    /**
     * Creates a new GuildRole.
     *
     * @param guildId the guild this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority (higher = more authority)
     */
    public GuildRole(UUID guildId, String name, int permissions, int priority) {
        this(guildId, name, permissions, priority, null);
    }

    /**
     * Creates a new GuildRole with creation metadata.
     *
     * @param guildId the guild this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority (higher = more authority)
     * @param createdBy the UUID of the player who created this role
     */
    public GuildRole(UUID guildId, String name, int permissions, int priority, UUID createdBy) {
        super(
            UUID.randomUUID().toString(),
            guildId,
            name,
            permissions,
            createdBy,
            createdBy != null ? System.currentTimeMillis() : null
        );
        this.priority = priority;
    }

    /**
     * Creates a new GuildRole with default priority (0).
     *
     * @param guildId the guild this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     */
    public GuildRole(UUID guildId, String name, int permissions) {
        this(guildId, name, permissions, 0);
    }

    /**
     * Full constructor for database restoration with creation metadata.
     *
     * @param id the role ID
     * @param guildId the guild this role belongs to
     * @param name the role name
     * @param permissions the permission bitfield
     * @param priority the role priority
     * @param createdBy the UUID of the creator (nullable for legacy roles)
     * @param createdAt the creation timestamp (nullable for legacy roles)
     */
    public GuildRole(String id, UUID guildId, String name, int permissions, int priority,
                     UUID createdBy, Long createdAt) {
        super(id, guildId, name, permissions, createdBy, createdAt);
        this.priority = priority;
    }

    /**
     * Creates the default "Member" role with basic permissions.
     */
    public static GuildRole createDefault(UUID guildId) {
        return new GuildRole(guildId, DEFAULT_ROLE_NAME, GuildPermission.defaultPermissions());
    }

    /**
     * Gets the guild ID this role belongs to.
     */
    public UUID getGuildId() {
        return getScopeId();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Checks if this role has higher priority than another role.
     */
    public boolean hasHigherPriorityThan(GuildRole other) {
        return this.priority > other.priority;
    }

    @Override
    public String toString() {
        return "GuildRole{" +
                "id='" + getId() + '\'' +
                ", guildId='" + getGuildId() + '\'' +
                ", name='" + getName() + '\'' +
                ", permissions=" + getPermissions() +
                ", priority=" + priority +
                '}';
    }
}

