package org.aincraft.subregion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages region permission storage and retrieval.
 * Single Responsibility: Region permission persistence only.
 */
public interface RegionPermissionRepository {
    /**
     * Saves a region permission (insert or update).
     *
     * @param permission the permission to save
     */
    void save(RegionPermission permission);

    /**
     * Deletes a permission by ID.
     *
     * @param permissionId the permission ID
     */
    void delete(String permissionId);

    /**
     * Finds a permission by its ID.
     *
     * @param id the permission ID
     * @return Optional containing the permission, or empty if not found
     */
    Optional<RegionPermission> findById(String id);

    /**
     * Finds all permissions for a specific region.
     *
     * @param regionId the region ID
     * @return list of permissions
     */
    List<RegionPermission> findByRegion(UUID regionId);

    /**
     * Finds a permission for a specific subject in a region.
     *
     * @param regionId    the region ID
     * @param subjectId   the subject ID (player UUID or role ID)
     * @param subjectType the subject type
     * @return Optional containing the permission, or empty if not found
     */
    Optional<RegionPermission> findByRegionAndSubject(UUID regionId, String subjectId, SubjectType subjectType);

    /**
     * Finds all player permissions for a region.
     *
     * @param regionId the region ID
     * @return list of player permissions
     */
    List<RegionPermission> findPlayerPermissions(UUID regionId);

    /**
     * Finds all role permissions for a region.
     *
     * @param regionId the region ID
     * @return list of role permissions
     */
    List<RegionPermission> findRolePermissions(UUID regionId);

    /**
     * Deletes all permissions for a region.
     *
     * @param regionId the region ID
     */
    void deleteAllByRegion(UUID regionId);
}
