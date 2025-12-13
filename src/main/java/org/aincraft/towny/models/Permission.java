package org.aincraft.towny.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a permission in the Towny system
 *
 * @deprecated Use {@link TownyPermission} enum and {@link PermissionSet} instead.
 * This class is maintained for backward compatibility only.
 * The bitwise flag system has been superseded by a type-safe enum-based approach.
 *
 * <p><b>Migration Guide:</b></p>
 * <ul>
 *   <li>Replace {@code Permission.Flag} constants with {@link TownyPermission} enum values</li>
 *   <li>Use {@link PermissionSet} instead of manual bitwise flag manipulation</li>
 *   <li>Example: {@code hasFlag(Permission.Flag.BUILD)} → {@code permSet.hasPermission(TownyPermission.BUILD)}</li>
 * </ul>
 *
 * @see TownyPermission
 * @see PermissionSet
 */
@Deprecated
public class Permission {

    private UUID id;
    private int flags; // Bitwise permission flags
    private String context; // town, plot, resident, global
    private String contextId; // town name, plot ID, resident UUID, etc.
    private String targetType; // resident, town, all, assistant, mayor
    private String targetId; // resident UUID, town name, etc.
    private LocalDateTime grantedAt;
    private UUID grantedBy; // UUID of admin who granted this permission

    /**
     * Permission contexts
     *
     * @deprecated Part of the deprecated Permission system. Use {@link TownyPermission} instead.
     */
    @Deprecated
    public static class Context {
        public static final String GLOBAL = "global";
        public static final String TOWN = "town";
        public static final String PLOT = "plot";
        public static final String RESIDENT = "resident";
        public static final String WORLD = "world";
    }

    /**
     * Permission flags using bitwise operations
     * Each permission is a power of 2, allowing combinations in a single integer
     *
     * @deprecated Use {@link TownyPermission} enum instead for type-safe permissions
     */
    @Deprecated
    public static class Flag {
        // Build permissions (bits 0-3)
        public static final int BUILD = 1 << 0;      // 1
        public static final int DESTROY = 1 << 1;    // 2
        public static final int SWITCH = 1 << 2;     // 4
        public static final int ITEM_USE = 1 << 3;   // 8

        // Town permissions (bits 4-7)
        public static final int CLAIM = 1 << 4;      // 16
        public static final int UNCLAIM = 1 << 5;    // 32
        public static final int SPAWN = 1 << 6;      // 64
        public static final int SET_SPAWN = 1 << 7;  // 128

        // Management permissions (bits 8-11)
        public static final int INVITE = 1 << 8;     // 256
        public static final int KICK = 1 << 9;       // 512
        public static final int PROMOTE = 1 << 10;   // 1024
        public static final int DEMOTE = 1 << 11;    // 2048

        // Economic permissions (bits 12-13)
        public static final int WITHDRAW = 1 << 12;  // 4096
        public static final int DEPOSIT = 1 << 13;   // 8192

        // Plot permissions (bits 14-16)
        public static final int PLOT_PERM = 1 << 14; // 16384
        public static final int PLOT_SET = 1 << 15;  // 32768
        public static final int PLOT_OWNER = 1 << 16;// 65536

        // Admin permissions (bits 17-20)
        public static final int ADMIN = 1 << 17;     // 131072
        public static final int ADMIN_TOWN = 1 << 18;// 262144
        public static final int ADMIN_PLOT = 1 << 19;// 524288
        public static final int ADMIN_RESIDENT = 1 << 20; // 1048576
        public static final int BYPASS = 1 << 21;    // 2097152

        // Convenience combinations
        public static final int BUILD_ALL = BUILD | DESTROY | SWITCH | ITEM_USE;
        public static final int TOWN_MANAGE = CLAIM | UNCLAIM | SPAWN | SET_SPAWN;
        public static final int MEMBER_MANAGE = INVITE | KICK | PROMOTE | DEMOTE;
        public static final int ECONOMIC = WITHDRAW | DEPOSIT;
        public static final int PLOT_MANAGE = PLOT_PERM | PLOT_SET | PLOT_OWNER;
        public static final int ADMIN_ALL = ADMIN | ADMIN_TOWN | ADMIN_PLOT | ADMIN_RESIDENT | BYPASS;
        public static final int ALL = BUILD_ALL | TOWN_MANAGE | MEMBER_MANAGE | ECONOMIC | PLOT_MANAGE | ADMIN_ALL;

        // Default permission sets
        public static final int DEFAULT_TOWN = BUILD | SWITCH | SPAWN | INVITE;
        public static final int DEFAULT_PLOT = BUILD | SWITCH | ITEM_USE;
        public static final int ASSISTANT_PERMS = DEFAULT_TOWN | CLAIM | UNCLAIM | KICK;
        public static final int MAYOR_PERMS = ASSISTANT_PERMS | MEMBER_MANAGE | WITHDRAW | SET_SPAWN | DEPOSIT;
        public static final int RESIDENT_PERMS = BUILD | SWITCH | SPAWN;
    }

    /**
     * Target types
     *
     * @deprecated Part of the deprecated Permission system. Use {@link TownyPermission} instead.
     */
    @Deprecated
    public static class Target {
        public static final String RESIDENT = "resident";
        public static final String TOWN = "town";
        public static final String ASSISTANT = "assistant";
        public static final String MAYOR = "mayor";
        public static final String ALL = "all";
        public static final String NATION = "nation";
        public static final String ALLY = "ally";
        public static final String ENEMY = "enemy";
    }

    /**
     * Default constructor for database mapping
     */
    public Permission() {
        this.id = UUID.randomUUID();
        this.grantedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new permission with flags
     * @param flags Permission flags (bitwise combination)
     * @param context Permission context
     * @param contextId Context ID
     */
    public Permission(int flags, String context, String contextId) {
        this();
        this.flags = flags;
        this.context = context;
        this.contextId = contextId;
        this.targetType = Target.ALL;
        this.targetId = null;
    }

    /**
     * Constructor for creating a targeted permission with flags
     * @param flags Permission flags (bitwise combination)
     * @param context Permission context
     * @param contextId Context ID
     * @param targetType Target type
     * @param targetId Target ID
     */
    public Permission(int flags, String context, String contextId,
                     String targetType, String targetId) {
        this(flags, context, contextId);
        this.targetType = targetType;
        this.targetId = targetId;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
    }

    // Business methods

    /**
     * Check if this permission has a specific flag
     * @param flag Permission flag to check
     * @return True if flag is set
     */
    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    /**
     * Add a permission flag
     * @param flag Permission flag to add
     */
    public void addFlag(int flag) {
        flags |= flag;
    }

    /**
     * Remove a permission flag
     * @param flag Permission flag to remove
     */
    public void removeFlag(int flag) {
        flags &= ~flag;
    }

    /**
     * Toggle a permission flag
     * @param flag Permission flag to toggle
     * @return True if flag is now set, false if it's now unset
     */
    public boolean toggleFlag(int flag) {
        flags ^= flag;
        return hasFlag(flag);
    }

    /**
     * Set multiple flags at once (overwrites existing flags)
     * @param newFlags New flags to set
     */
    public void overwriteFlags(int newFlags) {
        this.flags = newFlags;
    }

    /**
     * Add multiple flags at once
     * @param flagsToAdd Flags to add
     */
    public void addFlags(int flagsToAdd) {
        flags |= flagsToAdd;
    }

    /**
     * Remove multiple flags at once
     * @param flagsToRemove Flags to remove
     */
    public void removeFlags(int flagsToRemove) {
        flags &= ~flagsToRemove;
    }

    /**
     * Check if this permission applies to a specific resident
     * @param residentUuid Resident UUID
     * @return True if applies
     */
    public boolean appliesTo(UUID residentUuid) {
        switch (targetType) {
            case Target.ALL:
                return true;
            case Target.RESIDENT:
                return residentUuid.toString().equals(targetId);
            case Target.ASSISTANT:
            case Target.MAYOR:
                // These would need to be checked against resident's role in town
                return false; // Would need service to verify
            case Target.TOWN:
                // Would need to check if resident is in the specified town
                return false; // Would need service to verify
            default:
                return false;
        }
    }

    /**
     * Check if this permission applies to a specific town
     * @param townName Town name
     * @return True if applies
     */
    public boolean appliesToTown(String townName) {
        return Target.TOWN.equals(targetType) && townName.equals(targetId);
    }

    /**
     * Check if this permission has build-related flags
     * @return True if has build-related permissions
     */
    public boolean hasBuildPermissions() {
        return hasFlag(Flag.BUILD_ALL);
    }

    /**
     * Check if this permission has town management flags
     * @return True if has town management permissions
     */
    public boolean hasTownManagementPermissions() {
        return hasFlag(Flag.TOWN_MANAGE) || hasFlag(Flag.MEMBER_MANAGE);
    }

    /**
     * Check if this permission has admin flags
     * @return True if has admin permissions
     */
    public boolean hasAdminPermissions() {
        return hasFlag(Flag.ADMIN_ALL);
    }

    /**
     * Get all active flags as a list of flag names
     * @return List of active flag names
     */
    public java.util.List<String> getActiveFlagNames() {
        java.util.List<String> activeFlags = new java.util.ArrayList<>();

        if (hasFlag(Flag.BUILD)) activeFlags.add("BUILD");
        if (hasFlag(Flag.DESTROY)) activeFlags.add("DESTROY");
        if (hasFlag(Flag.SWITCH)) activeFlags.add("SWITCH");
        if (hasFlag(Flag.ITEM_USE)) activeFlags.add("ITEM_USE");
        if (hasFlag(Flag.CLAIM)) activeFlags.add("CLAIM");
        if (hasFlag(Flag.UNCLAIM)) activeFlags.add("UNCLAIM");
        if (hasFlag(Flag.SPAWN)) activeFlags.add("SPAWN");
        if (hasFlag(Flag.SET_SPAWN)) activeFlags.add("SET_SPAWN");
        if (hasFlag(Flag.INVITE)) activeFlags.add("INVITE");
        if (hasFlag(Flag.KICK)) activeFlags.add("KICK");
        if (hasFlag(Flag.PROMOTE)) activeFlags.add("PROMOTE");
        if (hasFlag(Flag.DEMOTE)) activeFlags.add("DEMOTE");
        if (hasFlag(Flag.WITHDRAW)) activeFlags.add("WITHDRAW");
        if (hasFlag(Flag.DEPOSIT)) activeFlags.add("DEPOSIT");
        if (hasFlag(Flag.PLOT_PERM)) activeFlags.add("PLOT_PERM");
        if (hasFlag(Flag.PLOT_SET)) activeFlags.add("PLOT_SET");
        if (hasFlag(Flag.PLOT_OWNER)) activeFlags.add("PLOT_OWNER");
        if (hasFlag(Flag.ADMIN)) activeFlags.add("ADMIN");
        if (hasFlag(Flag.ADMIN_TOWN)) activeFlags.add("ADMIN_TOWN");
        if (hasFlag(Flag.ADMIN_PLOT)) activeFlags.add("ADMIN_PLOT");
        if (hasFlag(Flag.ADMIN_RESIDENT)) activeFlags.add("ADMIN_RESIDENT");
        if (hasFlag(Flag.BYPASS)) activeFlags.add("BYPASS");

        return activeFlags;
    }

    /**
     * Get a user-friendly display name for this permission
     * @return Display name
     */
    public String getDisplayName() {
        if (context == null) return "Unknown Permission";

        return String.format("%s Permission (%s)",
                context.substring(0, 1).toUpperCase() + context.substring(1).toLowerCase(),
                getActiveFlagNames().size() + " flags");
    }

    /**
     * Create a copy of this permission with different flags
     * @param newFlags New flags
     * @return New permission with different flags
     */
    public Permission withFlags(int newFlags) {
        Permission copy = new Permission();
        copy.flags = newFlags;
        copy.context = this.context;
        copy.contextId = this.contextId;
        copy.targetType = this.targetType;
        copy.targetId = this.targetId;
        copy.grantedBy = this.grantedBy;
        return copy;
    }

    /**
     * Create a copy of this permission with a different target
     * @param newTargetType New target type
     * @param newTargetId New target ID
     * @return New permission with different target
     */
    public Permission withTarget(String newTargetType, String newTargetId) {
        Permission copy = new Permission();
        copy.flags = this.flags;
        copy.context = this.context;
        copy.contextId = this.contextId;
        copy.targetType = newTargetType;
        copy.targetId = newTargetId;
        copy.grantedBy = this.grantedBy;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Permission that = (Permission) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Permission{" +
                "flags=" + flags +
                " (" + Integer.toBinaryString(flags) + ")" +
                ", context='" + context + '\'' +
                ", contextId='" + contextId + '\'' +
                ", targetType='" + targetType + '\'' +
                ", targetId='" + targetId + '\'' +
                ", activeFlags=" + getActiveFlagNames() +
                '}';
    }
}