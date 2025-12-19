package org.aincraft.subregion;

import org.aincraft.AbstractRole;
import java.util.UUID;

/**
 * Represents a role scoped to a specific region.
 * Region roles have lower priority than guild roles in the permission hierarchy.
 */
public class RegionRole extends AbstractRole<UUID> {

    /**
     * Creates a new region role.
     */
    public RegionRole(UUID regionId, String name, int permissions, UUID createdBy) {
        super(
            UUID.randomUUID().toString(),
            regionId,
            name,
            permissions,
            createdBy,
            System.currentTimeMillis()
        );
    }

    /**
     * Constructor for loading from database.
     */
    public RegionRole(String id, String regionIdStr, String name, int permissions, long createdAt, UUID createdBy) {
        super(id, UUID.fromString(regionIdStr), name, permissions, createdBy, createdAt);
    }

    /**
     * Gets the region ID this role belongs to.
     */
    public UUID getRegionId() {
        return getScopeId();
    }

    @Override
    public String toString() {
        return "RegionRole{" +
                "id='" + getId() + '\'' +
                ", regionId='" + getRegionId() + '\'' +
                ", name='" + getName() + '\'' +
                ", permissions=" + getPermissions() +
                '}';
    }
}
