package org.aincraft.towny.services;

import org.aincraft.towny.models.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing permissions
 */
public interface PermissionService {

    /**
     * Check if a resident has a specific permission in a context
     * @param residentUuid Resident UUID
     * @param permission Permission node
     * @param context Permission context (town, plot, etc.)
     * @param contextId Context ID (town name, plot ID, etc.)
     * @return True if permission is granted
     */
    boolean hasPermission(UUID residentUuid, String permission, String context, String contextId);

    /**
     * Grant a permission to a resident
     * @param residentUuid Resident UUID
     * @param permission Permission node
     * @param context Permission context
     * @param contextId Context ID
     * @param value Permission value
     * @return True if granted successfully
     */
    boolean grantPermission(UUID residentUuid, String permission, String context, String contextId, boolean value);

    /**
     * Revoke a permission from a resident
     * @param residentUuid Resident UUID
     * @param permission Permission node
     * @param context Permission context
     * @param contextId Context ID
     * @return True if revoked successfully
     */
    boolean revokePermission(UUID residentUuid, String permission, String context, String contextId);

    /**
     * Get all permissions for a resident in a specific context
     * @param residentUuid Resident UUID
     * @param context Permission context
     * @param contextId Context ID
     * @return List of permissions
     */
    List<Permission> getResidentPermissions(UUID residentUuid, String context, String contextId);

    /**
     * Get all permissions for a context
     * @param context Permission context
     * @param contextId Context ID
     * @return List of permissions
     */
    List<Permission> getContextPermissions(String context, String contextId);

    /**
     * Set town permissions
     * @param townName Town name
     * @param permissions Map of permission nodes to values
     * @return True if set successfully
     */
    boolean setTownPermissions(String townName, List<Permission> permissions);

    /**
     * Set plot permissions
     * @param plotId Plot ID
     * @param permissions Map of permission nodes to values
     * @return True if set successfully
     */
    boolean setPlotPermissions(UUID plotId, List<Permission> permissions);

    /**
     * Check if a resident can build in a specific location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can build
     */
    boolean canBuild(UUID residentUuid, int x, int z, String world);

    /**
     * Check if a resident can destroy in a specific location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can destroy
     */
    boolean canDestroy(UUID residentUuid, int x, int z, String world);

    /**
     * Check if a resident can switch blocks (doors, levers, etc.) in a specific location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can switch
     */
    boolean canSwitch(UUID residentUuid, int x, int z, String world);

    /**
     * Check if a resident can use items in a specific location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can use items
     */
    boolean canUseItems(UUID residentUuid, int x, int z, String world);

    /**
     * Get default permissions for new towns
     * @return List of default permissions
     */
    List<Permission> getDefaultTownPermissions();

    /**
     * Get default permissions for new plots
     * @return List of default permissions
     */
    List<Permission> getDefaultPlotPermissions();

    /**
     * Check if a resident is town mayor
     * @param residentUuid Resident UUID
     * @param townName Town name
     * @return True if is mayor
     */
    boolean isTownMayor(UUID residentUuid, String townName);

    /**
     * Check if a resident is town assistant
     * @param residentUuid Resident UUID
     * @param townName Town name
     * @return True if is assistant
     */
    boolean isTownAssistant(UUID residentUuid, String townName);

    /**
     * Check if a resident owns a plot
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @return True if owns plot
     */
    boolean ownsPlot(UUID residentUuid, UUID plotId);

    /**
     * Check if a resident has town-level admin permissions
     * @param residentUuid Resident UUID
     * @param townName Town name
     * @return True if has admin permissions
     */
    boolean hasTownAdmin(UUID residentUuid, String townName);

    /**
     * Get all permission nodes available
     * @return List of permission nodes
     */
    List<String> getAllPermissionNodes();
}