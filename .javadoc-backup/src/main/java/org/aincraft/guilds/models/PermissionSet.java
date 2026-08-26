package org.aincraft.guilds.models;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple container for GuildPermission enums using EnumSet
 * Provides backward compatibility with legacy bitwise flags
 */
public class PermissionSet {

    private final EnumSet<GuildPermission> grantedPermissions;
    private final EnumSet<GuildPermission> deniedPermissions;

    public PermissionSet() {
        this.grantedPermissions = EnumSet.noneOf(GuildPermission.class);
        this.deniedPermissions = EnumSet.noneOf(GuildPermission.class);
    }

    public PermissionSet(Set<GuildPermission> granted) {
        this.grantedPermissions = copyOf(granted);
        this.deniedPermissions = EnumSet.noneOf(GuildPermission.class);
    }

    public PermissionSet(Set<GuildPermission> granted, Set<GuildPermission> denied) {
        this.grantedPermissions = copyOf(granted);
        this.deniedPermissions = copyOf(denied);
    }

    private static EnumSet<GuildPermission> copyOf(Set<GuildPermission> permissions) {
        return permissions.isEmpty()
                ? EnumSet.noneOf(GuildPermission.class)
                : EnumSet.copyOf(permissions);
    }

    /**
     * Check if permission is granted (and not explicitly denied)
     */
    public boolean hasPermission(GuildPermission permission) {
        return grantedPermissions.contains(permission) && !deniedPermissions.contains(permission);
    }

    /**
     * Grant a permission
     */
    public void grantPermission(GuildPermission permission) {
        grantedPermissions.add(permission);
        deniedPermissions.remove(permission); // Remove from denied if present
    }

    /**
     * Deny a permission explicitly
     */
    public void denyPermission(GuildPermission permission) {
        deniedPermissions.add(permission);
        grantedPermissions.remove(permission); // Remove from granted if present
    }

    /**
     * Revoke a permission (remove from both granted and denied)
     */
    public void revokePermission(GuildPermission permission) {
        grantedPermissions.remove(permission);
        deniedPermissions.remove(permission);
    }

    /**
     * Toggle a permission (grant if denied or not present, deny if granted)
     * @param permission Permission to toggle
     * @return True if permission is now granted, False if denied
     */
    public boolean togglePermission(GuildPermission permission) {
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
    public PermissionSet togglePermissions(Set<GuildPermission> permissions) {
        PermissionSet newlyGranted = new PermissionSet();
        for (GuildPermission permission : permissions) {
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
    public PermissionSet toggleCategory(GuildPermission.Category category) {
        Set<GuildPermission> categoryPermissions = GuildPermission.getByCategory(category)
                .stream()
                .collect(java.util.stream.Collectors.toSet());
        return togglePermissions(categoryPermissions);
    }

    /**
     * Add multiple permissions at once
     * @param permissions Permissions to grant
     */
    public void grantAll(Set<GuildPermission> permissions) {
        for (GuildPermission permission : permissions) {
            grantPermission(permission);
        }
    }

    /**
     * Remove multiple permissions at once
     * @param permissions Permissions to revoke
     */
    public void revokeAll(Set<GuildPermission> permissions) {
        for (GuildPermission permission : permissions) {
            revokePermission(permission);
        }
    }

    /**
     * Get all granted permissions
     */
    public Set<GuildPermission> getGrantedPermissions() {
        return copyOf(grantedPermissions);
    }

    /**
     * Get all denied permissions
     */
    public Set<GuildPermission> getDeniedPermissions() {
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
        return GuildPermission.toLegacyFlags(grantedPermissions);
    }

    /**
     * Create PermissionSet from legacy bitwise flags
     */
    public static PermissionSet fromLegacyFlags(int flags) {
        Set<GuildPermission> permissions = GuildPermission.fromLegacyFlags(flags);
        return new PermissionSet(permissions);
    }

    /**
     * Create a PermissionSet with default guild permissions
     */
    public static PermissionSet createDefaultGuild() {
        Set<GuildPermission> defaults = Set.of(
            GuildPermission.BUILD,
            GuildPermission.SWITCH,
            GuildPermission.SPAWN,
            GuildPermission.INVITE
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet with default plot permissions
     */
    public static PermissionSet createDefaultPlot() {
        Set<GuildPermission> defaults = Set.of(
            GuildPermission.BUILD,
            GuildPermission.SWITCH,
            GuildPermission.ITEM_USE
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet for guild assistants
     */
    public static PermissionSet createAssistant() {
        PermissionSet perms = createDefaultGuild();
        perms.grantPermission(GuildPermission.CLAIM);
        perms.grantPermission(GuildPermission.UNCLAIM);
        perms.grantPermission(GuildPermission.KICK);
        return perms;
    }

    /**
     * Create a PermissionSet for guild mayors
     */
    public static PermissionSet createMayor() {
        PermissionSet perms = createAssistant();
        perms.grantPermission(GuildPermission.PROMOTE);
        perms.grantPermission(GuildPermission.DEMOTE);
        perms.grantPermission(GuildPermission.WITHDRAW);
        perms.grantPermission(GuildPermission.SET_SPAWN);
        perms.grantPermission(GuildPermission.DEPOSIT);
        return perms;
    }

    /**
     * Create a PermissionSet for regular guild residents
     */
    public static PermissionSet createResident() {
        Set<GuildPermission> defaults = Set.of(
            GuildPermission.BUILD,
            GuildPermission.SWITCH,
            GuildPermission.SPAWN
        );
        return new PermissionSet(defaults);
    }

    /**
     * Create a PermissionSet with only build permissions
     */
    public static PermissionSet createBuildOnly() {
        Set<GuildPermission> buildPerms = Set.of(
            GuildPermission.BUILD,
            GuildPermission.DESTROY,
            GuildPermission.SWITCH,
            GuildPermission.ITEM_USE
        );
        return new PermissionSet(buildPerms);
    }

    /**
     * Create an admin PermissionSet with all permissions
     */
    public static PermissionSet createAdmin() {
        PermissionSet adminSet = new PermissionSet();
        for (GuildPermission permission : GuildPermission.values()) {
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
    public boolean hasAnyFromCategory(GuildPermission.Category category) {
        return grantedPermissions.stream().anyMatch(p -> p.getCategory() == category);
    }

    /**
     * Get all permissions from a specific category
     */
    public PermissionSet getCategoryPermissions(GuildPermission.Category category) {
        Set<GuildPermission> categoryPerms = grantedPermissions.stream()
                .filter(p -> p.getCategory() == category)
                .collect(Collectors.toSet());
        return new PermissionSet(categoryPerms);
    }

    /**
     * Remove all permissions from a specific category
     */
    public void removeCategory(GuildPermission.Category category) {
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
                  .map(GuildPermission::getDisplayName)
                  .collect(Collectors.joining(", ")))
              .append("]");
        }

        if (!deniedPermissions.isEmpty()) {
            if (sb.length() > 13) sb.append(", ");
            sb.append("denied=[")
              .append(deniedPermissions.stream()
                  .map(GuildPermission::getDisplayName)
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