package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a permission in the Guilds system
 *
 * @deprecated Use {@link GuildPermission} enum and {@link PermissionSet} instead.
 * This class is maintained for backward compatibility only.
 * The bitwise flag system has been superseded by a type-safe enum-based approach.
 *
 * <p><b>Migration Guide:</b></p>
 * <ul>
 *   <li>Use {@link GuildPermission} enum values and {@link PermissionSet} instead of manual bitwise flag manipulation</li>
 *   <li>Example: {@code hasFlag(GuildPermission.BUILD.getLegacyBitwiseValue())} → {@code permSet.hasPermission(GuildPermission.BUILD)}</li>
 * </ul>
 *
 * @see GuildPermission
 * @see PermissionSet
 */
@Deprecated
public class Permission {

    private UUID id;
    private int flags; // Bitwise permission flags
    private String context; // guild, plot, resident, global
    private String contextId; // guild name, plot ID, resident UUID, etc.
    private String targetType; // resident, guild, all, assistant, mayor
    private String targetId; // resident UUID, guild name, etc.
    private LocalDateTime grantedAt;
    private UUID grantedBy; // UUID of admin who granted this permission

    /**
     * Permission contexts
     *
     * @deprecated Part of the deprecated Permission system. Use {@link GuildPermission} instead.
     */
    @Deprecated
    public static class Context {
        public static final String GLOBAL = "global";
        public static final String GUILD = "town";
        public static final String PLOT = "plot";
        public static final String RESIDENT = "resident";
        public static final String WORLD = "world";
    }

    /**
     * Target types
     *
     * @deprecated Part of the deprecated Permission system. Use {@link GuildPermission} instead.
     */
    @Deprecated
    public static class Target {
        public static final String RESIDENT = "resident";
        public static final String GUILD = "town";
        public static final String ASSISTANT = "assistant";
        public static final String MAYOR = "mayor";
        public static final String ALL = "all";
        public static final String NATION = "alliance";
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
                // These would need to be checked against resident's role in guild
                return false; // Would need service to verify
            case Target.GUILD:
                // Would need to check if resident is in the specified guild
                return false; // Would need service to verify
            default:
                return false;
        }
    }

    /**
     * Check if this permission applies to a specific guild
     * @param guildName Guild name
     * @return True if applies
     */
    public boolean appliesToGuild(String guildName) {
        return Target.GUILD.equals(targetType) && guildName.equals(targetId);
    }

    /**
     * Check if this permission has build-related flags
     * @return True if has build-related permissions
     */
    public boolean hasBuildPermissions() {
        return hasFlag(GuildPermission.BUILD_ALL);
    }

    /**
     * Check if this permission has guild management flags
     * @return True if has guild management permissions
     */
    public boolean hasGuildManagementPermissions() {
        return hasFlag(GuildPermission.GUILD_MANAGE) || hasFlag(GuildPermission.MEMBER_MANAGE);
    }

    /**
     * Check if this permission has admin flags
     * @return True if has admin permissions
     */
    public boolean hasAdminPermissions() {
        return hasFlag(GuildPermission.ADMIN_ALL);
    }

    /**
     * Get all active flags as a list of flag names
     * @return List of active flag names
     */
    public java.util.List<String> getActiveFlagNames() {
        java.util.List<String> activeFlags = new java.util.ArrayList<>();

        if (hasFlag(GuildPermission.BUILD.getLegacyBitwiseValue())) activeFlags.add("BUILD");
        if (hasFlag(GuildPermission.DESTROY.getLegacyBitwiseValue())) activeFlags.add("DESTROY");
        if (hasFlag(GuildPermission.SWITCH.getLegacyBitwiseValue())) activeFlags.add("SWITCH");
        if (hasFlag(GuildPermission.ITEM_USE.getLegacyBitwiseValue())) activeFlags.add("ITEM_USE");
        if (hasFlag(GuildPermission.CLAIM.getLegacyBitwiseValue())) activeFlags.add("CLAIM");
        if (hasFlag(GuildPermission.UNCLAIM.getLegacyBitwiseValue())) activeFlags.add("UNCLAIM");
        if (hasFlag(GuildPermission.SPAWN.getLegacyBitwiseValue())) activeFlags.add("SPAWN");
        if (hasFlag(GuildPermission.SET_SPAWN.getLegacyBitwiseValue())) activeFlags.add("SET_SPAWN");
        if (hasFlag(GuildPermission.INVITE.getLegacyBitwiseValue())) activeFlags.add("INVITE");
        if (hasFlag(GuildPermission.KICK.getLegacyBitwiseValue())) activeFlags.add("KICK");
        if (hasFlag(GuildPermission.PROMOTE.getLegacyBitwiseValue())) activeFlags.add("PROMOTE");
        if (hasFlag(GuildPermission.DEMOTE.getLegacyBitwiseValue())) activeFlags.add("DEMOTE");
        if (hasFlag(GuildPermission.WITHDRAW.getLegacyBitwiseValue())) activeFlags.add("WITHDRAW");
        if (hasFlag(GuildPermission.DEPOSIT.getLegacyBitwiseValue())) activeFlags.add("DEPOSIT");
        if (hasFlag(GuildPermission.PLOT_PERM.getLegacyBitwiseValue())) activeFlags.add("PLOT_PERM");
        if (hasFlag(GuildPermission.PLOT_SET.getLegacyBitwiseValue())) activeFlags.add("PLOT_SET");
        if (hasFlag(GuildPermission.PLOT_OWNER.getLegacyBitwiseValue())) activeFlags.add("PLOT_OWNER");
        if (hasFlag(GuildPermission.ADMIN.getLegacyBitwiseValue())) activeFlags.add("ADMIN");
        if (hasFlag(GuildPermission.ADMIN_GUILD.getLegacyBitwiseValue())) activeFlags.add("ADMIN_TOWN");
        if (hasFlag(GuildPermission.ADMIN_PLOT.getLegacyBitwiseValue())) activeFlags.add("ADMIN_PLOT");
        if (hasFlag(GuildPermission.ADMIN_RESIDENT.getLegacyBitwiseValue())) activeFlags.add("ADMIN_RESIDENT");
        if (hasFlag(GuildPermission.BYPASS.getLegacyBitwiseValue())) activeFlags.add("BYPASS");

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