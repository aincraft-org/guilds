package org.aincraft;

import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for roles with permission bitfields.
 * Provides common functionality for both guild-scoped and region-scoped roles.
 *
 * @param <T> the type of the scope identifier (UUID for both guild and region)
 */
public abstract class AbstractRole<T> implements Permissible {
    protected final String id;
    protected final T scopeId;
    protected String name;
    protected int permissions;
    protected final UUID createdBy;
    protected final Long createdAt;

    /**
     * Full constructor for creating a role.
     *
     * @param id the role ID
     * @param scopeId the scope this role belongs to (guild ID or region ID)
     * @param name the role name
     * @param permissions the permission bitfield
     * @param createdBy the UUID of the creator (nullable for legacy roles)
     * @param createdAt the creation timestamp (nullable for legacy roles)
     */
    protected AbstractRole(String id, T scopeId, String name, int permissions,
                          UUID createdBy, Long createdAt) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.scopeId = Objects.requireNonNull(scopeId, "Scope ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.permissions = permissions;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public T getScopeId() {
        return scopeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
    }

    @Override
    public int getPermissions() {
        return permissions;
    }

    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    /**
     * Grants a permission to this role.
     */
    public void grantPermission(int permissionBit) {
        permissions |= permissionBit;
    }

    /**
     * Revokes a permission from this role.
     */
    public void revokePermission(int permissionBit) {
        permissions &= ~permissionBit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractRole<?> that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
