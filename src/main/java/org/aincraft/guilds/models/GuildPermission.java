package org.aincraft.guilds.models;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum-based permission system for Guilds
 * Replaces complex bitwise flags with type-safe, readable permissions
 */
public enum GuildPermission {

    // Build Permissions (Category: BUILD)
    BUILD("Build", "Build and place blocks", Category.BUILD, 1 << 0, 1),
    DESTROY("Destroy", "Break blocks and structures", Category.BUILD, 1 << 1, 2),
    SWITCH("Switch", "Use switches, doors, buttons", Category.BUILD, 1 << 2, 3),
    ITEM_USE("Item Use", "Use items and interact", Category.BUILD, 1 << 3, 4),

    // Town Management Permissions (Category: TOWN_MANAGEMENT)
    CLAIM("Claim", "Claim land for town", Category.TOWN_MANAGEMENT, 1 << 4, 5),
    UNCLAIM("Unclaim", "Unclaim town land", Category.TOWN_MANAGEMENT, 1 << 5, 6),
    SPAWN("Spawn", "Teleport to town spawn", Category.TOWN_MANAGEMENT, 1 << 6, 7),
    SET_SPAWN("Set Spawn", "Set town spawn location", Category.TOWN_MANAGEMENT, 1 << 7, 8),

    // Member Management Permissions (Category: MEMBER_MANAGEMENT)
    INVITE("Invite", "Invite residents to town", Category.MEMBER_MANAGEMENT, 1 << 8, 9),
    KICK("Kick", "Kick residents from town", Category.MEMBER_MANAGEMENT, 1 << 9, 10),
    PROMOTE("Promote", "Promote residents to assistant", Category.MEMBER_MANAGEMENT, 1 << 10, 11),
    DEMOTE("Demote", "Demote assistants to resident", Category.MEMBER_MANAGEMENT, 1 << 11, 12),

    // Economic Permissions (Category: ECONOMIC)
    WITHDRAW("Withdraw", "Withdraw from town bank", Category.ECONOMIC, 1 << 12, 13),
    DEPOSIT("Deposit", "Deposit to town bank", Category.ECONOMIC, 1 << 13, 14),

    // Plot Permissions (Category: PLOT)
    PLOT_PERM("Plot Permissions", "Manage plot permissions", Category.PLOT, 1 << 14, 15),
    PLOT_SET("Plot Settings", "Change plot settings", Category.PLOT, 1 << 15, 16),
    PLOT_OWNER("Plot Ownership", "Transfer plot ownership", Category.PLOT, 1 << 16, 17),

    // Admin Permissions (Category: ADMIN)
    ADMIN("Admin", "Full administrative access", Category.ADMIN, 1 << 17, 18),
    ADMIN_TOWN("Admin Town", "Town administration access", Category.ADMIN, 1 << 18, 19),
    ADMIN_PLOT("Admin Plot", "Plot administration access", Category.ADMIN, 1 << 19, 20),
    ADMIN_RESIDENT("Admin Resident", "Resident administration access", Category.ADMIN, 1 << 20, 21),
    BYPASS("Bypass", "Bypass all permission checks", Category.ADMIN, 1 << 21, 22);

    private final String displayName;
    private final String description;
    private final Category category;
    private final int legacyBitwiseValue;
    private final int sortOrder;

    GuildPermission(String displayName, String description, Category category, int legacyBitwiseValue, int sortOrder) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.legacyBitwiseValue = legacyBitwiseValue;
        this.sortOrder = sortOrder;
    }

    /**
     * Permission categories for organizational purposes
     */
    public enum Category {
        BUILD("Build Permissions", "Permissions related to building and interacting with blocks"),
        TOWN_MANAGEMENT("Town Management", "Permissions for managing town territory and infrastructure"),
        MEMBER_MANAGEMENT("Member Management", "Permissions for managing town members"),
        ECONOMIC("Economic", "Permissions related to town finances"),
        PLOT("Plot", "Permissions for managing individual plots"),
        ADMIN("Administrative", "Administrative and bypass permissions");

        private final String displayName;
        private final String description;

        Category(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // Getters
    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public int getLegacyBitwiseValue() {
        return legacyBitwiseValue;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * Convert legacy bitwise value to GuildPermission enum
     * @param legacyValue The legacy bitwise value
     * @return Optional containing the matching permission, or empty if not found
     */
    public static Optional<GuildPermission> fromLegacyValue(int legacyValue) {
        return Arrays.stream(values())
                .filter(permission -> permission.legacyBitwiseValue == legacyValue)
                .findFirst();
    }

    /**
     * Convert legacy bitwise flags to a set of GuildPermission enums
     * @param legacyFlags The legacy bitwise combination
     * @return Set of GuildPermission enums
     */
    public static Set<GuildPermission> fromLegacyFlags(int legacyFlags) {
        return Arrays.stream(values())
                .filter(permission -> (legacyFlags & permission.legacyBitwiseValue) != 0)
                .collect(Collectors.toSet());
    }

    /**
     * Convert a set of GuildPermission enums back to legacy bitwise flags
     * @param permissions The set of permissions
     * @return Legacy bitwise flags
     */
    public static int toLegacyFlags(Set<GuildPermission> permissions) {
        return permissions.stream()
                .mapToInt(GuildPermission::getLegacyBitwiseValue)
                .reduce(0, (a, b) -> a | b);
    }

    /**
     * Get all permissions in a specific category
     * @param category The category to filter by
     * @return List of permissions in the category
     */
    public static List<GuildPermission> getByCategory(Category category) {
        return Arrays.stream(values())
                .filter(permission -> permission.category == category)
                .sorted(Comparator.comparingInt(GuildPermission::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Get all permissions sorted by category and sort order
     * @return List of all permissions in organized order
     */
    public static List<GuildPermission> getAllSorted() {
        return Arrays.stream(values())
                .sorted(Comparator
                        .comparing((GuildPermission p) -> p.category.getDisplayName())
                        .thenComparingInt(GuildPermission::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * Check if this is a build-related permission
     */
    public boolean isBuildPermission() {
        return category == Category.BUILD;
    }

    /**
     * Check if this is a town management permission
     */
    public boolean isTownManagementPermission() {
        return category == Category.TOWN_MANAGEMENT;
    }

    /**
     * Check if this is a member management permission
     */
    public boolean isMemberManagementPermission() {
        return category == Category.MEMBER_MANAGEMENT;
    }

    /**
     * Check if this is an economic permission
     */
    public boolean isEconomicPermission() {
        return category == Category.ECONOMIC;
    }

    /**
     * Check if this is a plot-related permission
     */
    public boolean isPlotPermission() {
        return category == Category.PLOT;
    }

    /**
     * Check if this is an administrative permission
     */
    public boolean isAdminPermission() {
        return category == Category.ADMIN;
    }

    // Legacy convenience methods for backward compatibility

    /**
     * Legacy compatibility - get build permissions combination
     */
    public static final int BUILD_ALL = BUILD.legacyBitwiseValue | DESTROY.legacyBitwiseValue |
                                         SWITCH.legacyBitwiseValue | ITEM_USE.legacyBitwiseValue;

    /**
     * Legacy compatibility - get town management permissions combination
     */
    public static final int TOWN_MANAGE = CLAIM.legacyBitwiseValue | UNCLAIM.legacyBitwiseValue |
                                         SPAWN.legacyBitwiseValue | SET_SPAWN.legacyBitwiseValue;

    /**
     * Legacy compatibility - get member management permissions combination
     */
    public static final int MEMBER_MANAGE = INVITE.legacyBitwiseValue | KICK.legacyBitwiseValue |
                                           PROMOTE.legacyBitwiseValue | DEMOTE.legacyBitwiseValue;

    /**
     * Legacy compatibility - get economic permissions combination
     */
    public static final int ECONOMIC = WITHDRAW.legacyBitwiseValue | DEPOSIT.legacyBitwiseValue;

    /**
     * Legacy compatibility - get plot management permissions combination
     */
    public static final int PLOT_MANAGE = PLOT_PERM.legacyBitwiseValue | PLOT_SET.legacyBitwiseValue |
                                          PLOT_OWNER.legacyBitwiseValue;

    /**
     * Legacy compatibility - get all admin permissions combination
     */
    public static final int ADMIN_ALL = ADMIN.legacyBitwiseValue | ADMIN_TOWN.legacyBitwiseValue |
                                        ADMIN_PLOT.legacyBitwiseValue | ADMIN_RESIDENT.legacyBitwiseValue |
                                        BYPASS.legacyBitwiseValue;

    /**
     * Legacy compatibility - get all permissions combination
     */
    public static final int ALL = BUILD_ALL | TOWN_MANAGE | MEMBER_MANAGE | ECONOMIC | PLOT_MANAGE | ADMIN_ALL;

    @Override
    public String toString() {
        return String.format("%s (%s) - %s", displayName, category.getDisplayName(), description);
    }
}