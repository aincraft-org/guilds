package org.aincraft.subregion;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a permission override for a specific player or role in a subregion.
 * Allows fine-grained access control beyond guild-level permissions.
 * Single Responsibility: Region permission data model.
 */
public class RegionPermission {
    private final String id;
    private final UUID regionId;
    private final String subjectId;  // Player UUID or Role ID
    private final SubjectType subjectType;
    private int permissions;  // Bitfield of allowed permissions
    private final long createdAt;
    private final UUID createdBy;

    /**
     * Creates a new region permission.
     *
     * @param regionId    the region ID this permission applies to
     * @param subjectId   the subject ID (player UUID or role ID)
     * @param subjectType the type of subject
     * @param permissions the permission bitfield
     * @param createdBy   the player who created this permission
     */
    public RegionPermission(UUID regionId, String subjectId, SubjectType subjectType,
                          int permissions, UUID createdBy) {
        this.id = UUID.randomUUID().toString();
        this.regionId = Objects.requireNonNull(regionId, "Region ID cannot be null");
        this.subjectId = Objects.requireNonNull(subjectId, "Subject ID cannot be null");
        this.subjectType = Objects.requireNonNull(subjectType, "Subject type cannot be null");
        this.permissions = permissions;
        this.createdAt = System.currentTimeMillis();
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
    }

    /**
     * Creates a region permission with existing data (for database restoration).
     *
     * @param id          existing permission ID
     * @param regionId    the region ID
     * @param subjectId   the subject ID
     * @param subjectType the subject type
     * @param permissions the permission bitfield
     * @param createdAt   creation timestamp
     * @param createdBy   creator UUID
     */
    public RegionPermission(String id, UUID regionId, String subjectId, SubjectType subjectType,
                          int permissions, long createdAt, UUID createdBy) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.regionId = Objects.requireNonNull(regionId, "Region ID cannot be null");
        this.subjectId = Objects.requireNonNull(subjectId, "Subject ID cannot be null");
        this.subjectType = Objects.requireNonNull(subjectType, "Subject type cannot be null");
        this.permissions = permissions;
        this.createdAt = createdAt;
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
    }

    /**
     * Checks if a specific permission bit is set.
     *
     * @param permissionBit the permission bit to check
     * @return true if the permission is granted
     */
    public boolean hasPermission(int permissionBit) {
        return (permissions & permissionBit) != 0;
    }

    /**
     * Adds a permission bit.
     *
     * @param permissionBit the permission bit to add
     */
    public void addPermission(int permissionBit) {
        permissions |= permissionBit;
    }

    /**
     * Removes a permission bit.
     *
     * @param permissionBit the permission bit to remove
     */
    public void removePermission(int permissionBit) {
        permissions &= ~permissionBit;
    }

    // Getters
    public String getId() {
        return id;
    }

    public UUID getRegionId() {
        return regionId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public int getPermissions() {
        return permissions;
    }

    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionPermission)) return false;
        RegionPermission that = (RegionPermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RegionPermission{" +
                "id='" + id + '\'' +
                ", regionId='" + regionId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", subjectType=" + subjectType +
                ", permissions=" + permissions +
                '}';
    }
}
