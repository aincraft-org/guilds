package org.aincraft.towny.models;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple container for TownyPermission enums using EnumSet
 * Provides backward compatibility with legacy bitwise flags
 */
public class PermissionSet {

    private final EnumSet<TownyPermission> grantedPermissions;
    private final EnumSet<TownyPermission> deniedPermissions;

    public PermissionSet() {
        this.grantedPermissions = EnumSet.noneOf(TownyPermission.class);
        this.deniedPermissions = EnumSet.noneOf(TownyPermission.class);
    }

    public PermissionSet(Set<TownyPermission> granted) {
        this.grantedPermissions = copyOf(granted);
        this.deniedPermissions = EnumSet.noneOf(TownyPermission.class);
    }

    public PermissionSet(Set<TownyPermission> granted, Set<TownyPermission> denied) {
        this.grantedPermissions = copyOf(granted);
        this.deniedPermissions = copyOf(denied);
    }

    private static EnumSet<TownyPermission> copyOf(Set<TownyPermission> permissions) {
        return permissions.isEmpty()
                ? EnumSet.noneOf(TownyPermission.class)
                : EnumSet.copyOf(permissions);
    }

    /**
     * Check if permission is granted (and not explicitly denied)
     */
    public boolean hasPermission(TownyPermission permission) {
        return grantedPermissions.contains(permission) && !deniedPermissions.contains(permission);
    }

    /**
     * Grant a permission
     */
    public void grantPermission(TownyPermission permission) {
        grantedPermissions.add(permission);
        deniedPermissions.remove(permission); // Remove from denied if present
    }

    /**
     * Deny a permission explicitly
     */
    public void denyPermission(TownyPermission permission) {
        deniedPermissions.add(permission);
        grantedPermissions.remove(permission); // Remove from granted if present
    }

    /**
     * Revoke a permission (remove from both granted and denied)
     */
    public void revokePermission(TownyPermission permission) {
        grantedPermissions.remove(permission);
        deniedPermissions.remove(permission);
    }

    /**
     * Toggle a permission (grant if denied or not present, deny if granted)
     * @param permission Permission to toggle
     * @return True if permission is now granted, False if denied
     */
    public boolean togglePermission(TownyPermission permission) {
        if (grantedPermissions.contains(permission)) {
            // Currently granted - switch to denied
            denyPermission(permission);
            return false;
        } else {
            // Currently denied or not present - grant it
            grantPermission(permission);
            return true;
        }
    }

    /**
     * Toggle multiple permissions at once
     * @param permissions Permissions to toggle
     * @return PermissionSet containing only the permissions that are now granted
     */
    public PermissionSet togglePermissions(Set<TownyPermission> permissions) {
        PermissionSet newlyGranted = new PermissionSet();
        for (TownyPermission permission : permissions) {
            if (togglePermission(permission)) {
                newlyGranted.grantPermission(permission);
            }
        }
        return newlyGranted;
    }

    /**
     * Toggle all permissions in a category
     * @param category Category of permissions to toggle
     * @return PermissionSet containing newly granted permissions
     */
    public PermissionSet toggleCategory(TownyPermission.Category category) {
        Set<TownyPermission> categoryPermissions = TownyPermission.getByCategory(category)
                .stream()
                .collect(java.util.stream.Collectors.toSet());
        return togglePermissions(categoryPermissions);
    }

    /**
     * Add multiple permissions at once
     * @param permissions Permissions to grant
     */
    public void grantAll(Set<TownyPermission> permissions) {
        for (TownyPermission permission : permissions) {
            grantPermission(permission);
        }
    }

    /**
     * Remove multiple permissions at once
     * @param permissions Permissions to revoke
     */
    public void revokeAll(Set<TownyPermission> permissions) {
        for (TownyPermission permission : permissions) {
            revokePermission(permission);
        }
    }

    /**
     * Get all granted permissions
     */
    public Set<TownyPermission> getGrantedPermissions() {
        return copyOf(grantedPermissions);
    }

    /**
     * Get all denied permissions
     */
    public Set<TownyPermission> getDeniedPermissions() {
        return copyOf(deniedPermissions);
    }

    /**
     * Check if the permission set is empty
     */
    public boolean isEmpty() {
        return grantedPermissions.isEmpty() && deniedPermissions.isEmpty();
    }

    /**
     * Get total number of permissions (granted + denied)
     */
    public int size() {
        return grantedPermissions.size() + deniedPermissions.size();
    }

    /**
     * Clear all permissions
     */
    public void clear() {
        grantedPermissions.clear();
        deniedPermissions.clear();
    }

    /**
     * Convert to legacy bitwise flags
     */
    public int toLegacyFlags() {
        return TownyPermission.toLegacyFlags(grantedPermissions);
    }

    /**
     * Create PermissionSet from legacy bitwise flags
     */
    public static PermissionSet fromLegacyFlags(int flags) {
        Set<TownyPermission> permissions = TownyPermission.fromLegacyFlags(flags);
        return new PermissionSet(permissions);
    }

    /**
     * Create a PermissionSet with default town permissions
     */
    public static PermissionSet createDefaultTown() {
        Set<TownyPermission> defaults = Set.of(
            TownyPermission.BUILD,
            TownyPermission.SWITCH,
            TownyPermission.SPAWN,
            TownyPermission.INVITE
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet with default plot permissions
     */
    public static PermissionSet createDefaultPlot() {
        Set<TownyPermission> defaults = Set.of(
            TownyPermission.BUILD,
            TownyPermission.SWITCH,
            TownyPermission.ITEM_USE
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet for town assistants
     */
    public static PermissionSet createAssistant() {
        PermissionSet perms = createDefaultTown();
        perms.grantPermission(TownyPermission.CLAIM);
        perms.grantPermission(TownyPermission.UNCLAIM);
        perms.grantPermission(TownyPermission.KICK);
        return perms;
    }

    /**
     * Create a PermissionSet for town mayors
     */
    public static PermissionSet createMayor() {
        PermissionSet perms = createAssistant();
        perms.grantPermission(TownyPermission.PROMOTE);
        perms.grantPermission(TownyPermission.DEMOTE);
        perms.grantPermission(TownyPermission.WITHDRAW);
        perms.grantPermission(TownyPermission.SET_SPAWN);
        perms.grantPermission(TownyPermission.DEPOSIT);
        return perms;
    }

    /**
     * Create a PermissionSet for regular town residents
     */
    public static PermissionSet createResident() {
        Set<TownyPermission> defaults = Set.of(
            TownyPermission.BUILD,
            TownyPermission.SWITCH,
            TownyPermission.SPAWN
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet with only build permissions
     */
    public static PermissionSet createBuildOnly() {
        Set<TownyPermission> buildPerms = Set.of(
            TownyPermission.BUILD,
            TownyPermission.DESTROY,
            TownyPermission.SWITCH,
            TownyPermission.ITEM_USE
        );
        return new PermissionSet(buildPerms);
    }

    /**
     * Create an admin PermissionSet with all permissions
     */
    public static PermissionSet createAdmin() {
        PermissionSet adminSet = new PermissionSet();
        for (TownyPermission permission : TownyPermission.values()) {
            adminSet.grantPermission(permission);
        }
        return adminSet;
    }

    /**
     * Copy this PermissionSet
     */
    public PermissionSet copy() {
        return new PermissionSet(
            EnumSet.copyOf(grantedPermissions),
            EnumSet.copyOf(deniedPermissions)
        );
    }

    /**
     * Check if this PermissionSet has any permissions from a specific category
     */
    public boolean hasAnyFromCategory(TownyPermission.Category category) {
        return grantedPermissions.stream().anyMatch(p -> p.getCategory() == category);
    }

    /**
     * Get all permissions from a specific category
     */
    public PermissionSet getCategoryPermissions(TownyPermission.Category category) {
        Set<TownyPermission> categoryPerms = grantedPermissions.stream()
                .filter(p -> p.getCategory() == category)
                .collect(Collectors.toSet());
        return new PermissionSet(categoryPerms);
    }

    /**
     * Remove all permissions from a specific category
     */
    public void removeCategory(TownyPermission.Category category) {
        grantedPermissions.removeIf(p -> p.getCategory() == category);
        deniedPermissions.removeIf(p -> p.getCategory() == category);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PermissionSet{");

        if (!grantedPermissions.isEmpty()) {
            sb.append("granted=[")
              .append(grantedPermissions.stream()
                  .map(TownyPermission::getDisplayName)
                  .collect(Collectors.joining(", ")))
              .append("]");
        }

        if (!deniedPermissions.isEmpty()) {
            if (sb.length() > 13) sb.append(", ");
            sb.append("denied=[")
              .append(deniedPermissions.stream()
                  .map(TownyPermission::getDisplayName)
                  .collect(Collectors.joining(", ")))
              .append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PermissionSet other = (PermissionSet) obj;
        return grantedPermissions.equals(other.grantedPermissions) &&
               deniedPermissions.equals(other.deniedPermissions);
    }

    @Override
    public int hashCode() {
        return grantedPermissions.hashCode() * 31 + deniedPermissions.hashCode();
    }
}