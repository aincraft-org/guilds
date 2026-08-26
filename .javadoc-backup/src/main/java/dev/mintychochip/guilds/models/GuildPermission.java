package dev.mintychochip.guilds.models;

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
    /** The build constant. */
    BUILD("Build", "Build and place blocks", Category.BUILD, 1 << 0, 1),
    /** The destroy constant. */
    DESTROY("Destroy", "Break blocks and structures", Category.BUILD, 1 << 1, 2),
    /** The switch constant. */
    SWITCH("Switch", "Use switches, doors, buttons", Category.BUILD, 1 << 2, 3),
    /** The item use constant. */
    ITEM_USE("Item Use", "Use items and interact", Category.BUILD, 1 << 3, 4),

    // Guild Management Permissions (Category: GUILD_MANAGEMENT)
    /** The claim constant. */
    CLAIM("Claim", "Claim land for guild", Category.GUILD_MANAGEMENT, 1 << 4, 5),
    /** The unclaim constant. */
    UNCLAIM("Unclaim", "Unclaim guild land", Category.GUILD_MANAGEMENT, 1 << 5, 6),
    /** The spawn constant. */
    SPAWN("Spawn", "Teleport to guild spawn", Category.GUILD_MANAGEMENT, 1 << 6, 7),
    /** The set spawn constant. */
    SET_SPAWN("Set Spawn", "Set guild spawn location", Category.GUILD_MANAGEMENT, 1 << 7, 8),

    // Member Management Permissions (Category: MEMBER_MANAGEMENT)
    /** The invite constant. */
    INVITE("Invite", "Invite residents to guild", Category.MEMBER_MANAGEMENT, 1 << 8, 9),
    /** The kick constant. */
    KICK("Kick", "Kick residents from guild", Category.MEMBER_MANAGEMENT, 1 << 9, 10),
    /** The promote constant. */
    PROMOTE("Promote", "Promote residents to assistant", Category.MEMBER_MANAGEMENT, 1 << 10, 11),
    /** The demote constant. */
    DEMOTE("Demote", "Demote assistants to resident", Category.MEMBER_MANAGEMENT, 1 << 11, 12),

    // Economic Permissions (Category: ECONOMIC)
    /** The withdraw constant. */
    WITHDRAW("Withdraw", "Withdraw from guild bank", Category.ECONOMIC, 1 << 12, 13),
    /** The deposit constant. */
    DEPOSIT("Deposit", "Deposit to guild bank", Category.ECONOMIC, 1 << 13, 14),

    // Plot Permissions (Category: PLOT)
    /** The plot perm constant. */
    PLOT_PERM("Plot Permissions", "Manage plot permissions", Category.PLOT, 1 << 14, 15),
    /** The plot set constant. */
    PLOT_SET("Plot Settings", "Change plot settings", Category.PLOT, 1 << 15, 16),
    /** The plot owner constant. */
    PLOT_OWNER("Plot Ownership", "Transfer plot ownership", Category.PLOT, 1 << 16, 17),

    // Admin Permissions (Category: ADMIN)
    /** The admin constant. */
    ADMIN("Admin", "Full administrative access", Category.ADMIN, 1 << 17, 18),
    /** The admin guild constant. */
    ADMIN_GUILD("Admin Guild", "Guild administration access", Category.ADMIN, 1 << 18, 19),
    /** The admin plot constant. */
    ADMIN_PLOT("Admin Plot", "Plot administration access", Category.ADMIN, 1 << 19, 20),
    /** The admin resident constant. */
    ADMIN_RESIDENT("Admin Resident", "Resident administration access", Category.ADMIN, 1 << 20, 21),
    /** The bypass constant. */
    BYPASS("Bypass", "Bypass all permission checks", Category.ADMIN, 1 << 21, 22);

    /** The display name. */
    private final String displayName;
    /** The description. */
    private final String description;
    /** The category. */
    private final Category category;
    /** The legacy bitwise value. */
    private final int legacyBitwiseValue;
    /** The sort order. */
    private final int sortOrder;

    /**
     * Creates a new  instance.
     * @param displayName the display name
     * @param description the description
     * @param category the category
     * @param legacyBitwiseValue the legacy bitwise value
     * @param sortOrder the sort order
     */
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
        /** The build constant. */
        BUILD("Build Permissions", "Permissions related to building and interacting with blocks"),
        /** The guild management constant. */
        GUILD_MANAGEMENT("Guild Management", "Permissions for managing guild territory and infrastructure"),
        /** The member management constant. */
        MEMBER_MANAGEMENT("Member Management", "Permissions for managing guild members"),
        /** The economic constant. */
        ECONOMIC("Economic", "Permissions related to guild finances"),
        /** The plot constant. */
        PLOT("Plot", "Permissions for managing individual plots"),
        /** The admin constant. */
        ADMIN("Administrative", "Administrative and bypass permissions");

        /** The display name. */
        private final String displayName;
        /** The description. */
        private final String description;

        /**
         * Creates a new  instance.
         * @param displayName the display name
         * @param description the description
         */
        Category(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        /**
         * Returns the display name.
         * @return the result
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the description.
         * @return the result
         */
        public String getDescription() {
            return description;
        }
    }

    // Getters
    /**
     * Returns the display name.
     * @return the result
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the description.
     * @return the result
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the category.
     * @return the result
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Returns the legacy bitwise value.
     * @return the result
     */
    public int getLegacyBitwiseValue() {
        return legacyBitwiseValue;
    }

    /**
     * Returns the sort order.
     * @return the result
     */
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
     * Check if this is a guild management permission
     */
    public boolean isGuildManagementPermission() {
        return category == Category.GUILD_MANAGEMENT;
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
     * Legacy compatibility - get guild management permissions combination
     */
    public static final int GUILD_MANAGE = CLAIM.legacyBitwiseValue | UNCLAIM.legacyBitwiseValue |
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
    public static final int ADMIN_ALL = ADMIN.legacyBitwiseValue | ADMIN_GUILD.legacyBitwiseValue |
                                        ADMIN_PLOT.legacyBitwiseValue | ADMIN_RESIDENT.legacyBitwiseValue |
                                        BYPASS.legacyBitwiseValue;

    /**
     * Legacy compatibility - get all permissions combination
     */
    public static final int ALL = BUILD_ALL | GUILD_MANAGE | MEMBER_MANAGE | ECONOMIC | PLOT_MANAGE | ADMIN_ALL;

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", displayName, category.getDisplayName(), description);
    }
}