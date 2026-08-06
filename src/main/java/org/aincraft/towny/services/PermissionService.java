package org.aincraft.towny.services;

import org.aincraft.towny.models.Permission;
import org.aincraft.towny.models.TownyPermission;
import org.aincraft.towny.models.PermissionSet;

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
     * Check if a resident can interact with entities (damage, use) in a specific location
     * This includes item frames, armor stands, animals, vehicles, etc.
     * Uses the same permission hierarchy as canDestroy (owner > plot perms > town perms > public)
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can interact with entities
     */
    boolean canInteractWithEntity(UUID residentUuid, int x, int z, String world);

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
     * Grant a specific permission flag to a resident in a town
     * @param residentUuid Resident UUID (null for all residents)
     * @param townName Town name
     * @param permissionFlag Permission flag (e.g., TownyPermission.SET_SPAWN.getLegacyBitwiseValue())
     * @return True if granted successfully
     */
    boolean grantTownPermission(UUID residentUuid, String townName, int permissionFlag);

    /**
     * Grant multiple permission flags to a resident in a town
     * @param residentUuid Resident UUID (null for all residents)
     * @param townName Town name
     * @param permissionFlags Combined permission flags
     * @return True if granted successfully
     */
    boolean grantTownPermissions(UUID residentUuid, String townName, int permissionFlags);

    /**
     * Get all permission nodes available
     * @return List of permission nodes
     */
    List<String> getAllPermissionNodes();

    // Plot-specific permission methods

    /**
     * Check if a resident can claim a plot at the specified location
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if can claim plot
     */
    boolean canClaimPlot(UUID residentUuid, int x, int z, String world);

    /**
     * Check if a resident can buy a specific plot
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @return True if can buy plot
     */
    boolean canBuyPlot(UUID residentUuid, UUID plotId);

    /**
     * Check if a resident can manage a specific plot (set permissions, type, etc.)
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @return True if can manage plot
     */
    boolean canManagePlot(UUID residentUuid, UUID plotId);

    /**
     * Check if a resident has a specific permission on a plot
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @param permissionFlag Permission flag to check
     * @return True if has permission
     */
    boolean hasPlotPermission(UUID residentUuid, UUID plotId, int permissionFlag);

    /**
     * Check if a resident can claim plots for their town
     * @param residentUuid Resident UUID
     * @param townName Town name
     * @return True if can claim for town
     */
    boolean canClaimForTown(UUID residentUuid, String townName);

    /**
     * Check if a resident has plot management permissions in their town
     * @param residentUuid Resident UUID
     * @param townName Town name
     * @return True if has plot management permissions
     */
    boolean hasPlotManagementPermissions(UUID residentUuid, String townName);

    /**
     * Evaluate plot permission with inheritance (global -> town -> plot)
     * @param residentUuid Resident UUID
     * @param plotId Plot ID
     * @param permissionFlag Permission flag to check
     * @return Permission evaluation result
     */
    PermissionEvaluationResult evaluatePlotPermission(UUID residentUuid, UUID plotId, int permissionFlag);

    // ==================== NEW ENUM-BASED METHODS ====================

    /**
     * Check if a resident has a specific permission using the new enum system
     * @param residentUuid Resident UUID
     * @param permission Permission enum
     * @param context Permission context (town, plot, etc.)
     * @param contextId Context ID (town name, plot ID, etc.)
     * @return Permission evaluation result with details
     */
    PermissionEvaluationResult hasPermission(UUID residentUuid, TownyPermission permission, String context, String contextId);

    /**
     * Grant a permission using the new enum system
     * @param residentUuid Resident UUID (null for all residents)
     * @param permission Permission enum to grant
     * @param context Permission context
     * @param contextId Context ID
     * @return True if granted successfully
     */
    boolean grantPermission(UUID residentUuid, TownyPermission permission, String context, String contextId);

    /**
     * Deny a permission explicitly using the new enum system
     * @param residentUuid Resident UUID (null for all residents)
     * @param permission Permission enum to deny
     * @param context Permission context
     * @param contextId Context ID
     * @return True if denied successfully
     */
    boolean denyPermission(UUID residentUuid, TownyPermission permission, String context, String contextId);

    /**
     * Revoke a permission using the new enum system
     * @param residentUuid Resident UUID (null for all residents)
     * @param permission Permission enum to revoke
     * @param context Permission context
     * @param contextId Context ID
     * @return True if revoked successfully
     */
    boolean revokePermission(UUID residentUuid, TownyPermission permission, String context, String contextId);

    /**
     * Get permission set for a resident in a specific context
     * @param residentUuid Resident UUID
     * @param context Permission context
     * @param contextId Context ID
     * @return PermissionSet containing granted and denied permissions
     */
    PermissionSet getPermissionSet(UUID residentUuid, String context, String contextId);

    /**
     * Set multiple permissions at once using PermissionSet
     * @param residentUuid Resident UUID (null for all residents)
     * @param permissionSet Set of permissions to apply
     * @param context Permission context
     * @param contextId Context ID
     * @return True if set successfully
     */
    boolean setPermissionSet(UUID residentUuid, PermissionSet permissionSet, String context, String contextId);

    /**
     * Evaluate a permission with full context using enum system
     * @param residentUuid Resident UUID
     * @param context Permission context
     * @param contextId Context ID
     * @param permission Permission enum to evaluate
     * @return Detailed evaluation result
     */
    PermissionEvaluationResult evaluatePermission(UUID residentUuid, String context, String contextId, TownyPermission permission);

    /**
     * Get default permissions for a specific role
     * @param role Role name (mayor, assistant, resident)
     * @return PermissionSet with default permissions for the role
     */
    PermissionSet getDefaultPermissions(String role);

    /**
     * Check cache statistics for performance monitoring
     * @return Cache statistics
     */
    String getCacheStatistics();

    /**
     * Clear permission cache (useful after permission changes)
     */
    void clearCache();

    /**
     * Clear cache for a specific resident
     * @param residentUuid Resident UUID
     */
    void clearResidentCache(UUID residentUuid);

    // Town toggle system integration methods

    /**
     * Check if PvP is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if PvP is enabled, false otherwise
     */
    boolean isPvpEnabledAtLocation(int x, int z, String world);

    /**
     * Check if fire spread is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if fire spread is enabled, false otherwise
     */
    boolean isFireEnabledAtLocation(int x, int z, String world);

    /**
     * Check if explosions are enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if explosions are enabled, false otherwise
     */
    boolean areExplosionsEnabledAtLocation(int x, int z, String world);

    /**
     * Check if mob spawning is enabled in a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if mob spawning is enabled, false otherwise
     */
    boolean areMobsEnabledAtLocation(int x, int z, String world);

    /**
     * Check if a town has public access at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return True if public access is enabled, false otherwise
     */
    boolean isPublicAccessEnabledAtLocation(int x, int z, String world);

    /**
     * Get all toggle states for a town at the specified location
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @return Map of toggle states, empty if no town found at location
     */
    java.util.Map<String, Boolean> getTogglesAtLocation(int x, int z, String world);
}