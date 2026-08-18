package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a player (resident) in the Guilds system
 */
public class  Resident {

    /** The uuid. */
    private UUID uuid;
    /** The name. */
    private String name;
    /** The guild. */
    private String guild;
    /** The last online. */
    private long lastOnline;
    /** The is online. */
    private boolean isOnline;
    /** The permissions. */
    private Map<String, Boolean> permissions;
    /** The joined at. */
    private LocalDateTime joinedAt;

    /**
     * Default constructor for database mapping
     */
    public Resident() {
        this.permissions = new HashMap<>();
        this.joinedAt = LocalDateTime.now();
        this.lastOnline = System.currentTimeMillis();
    }

    /**
     * Constructor for creating a new resident
     * @param uuid Player UUID
     * @param name Player name
     */
    public Resident(UUID uuid, String name) {
        this();
        this.uuid = uuid;
        this.name = name;
    }

    // Getters and Setters
    /**
     * Returns the uuid.
     * @return the result
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Sets the uuid.
     * @param uuid the uuid
     */
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Returns the name.
     * @return the result
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the guild.
     * @return the result
     */
    public String getGuild() {
        return guild;
    }

    /**
     * Sets the guild.
     * @param guild the guild
     */
    public void setGuild(String guild) {
        this.guild = guild;
    }

    /**
     * Returns the last online.
     * @return the result
     */
    public long getLastOnline() {
        return lastOnline;
    }

    /**
     * Sets the last online.
     * @param lastOnline the last online
     */
    public void setLastOnline(long lastOnline) {
        this.lastOnline = lastOnline;
    }

    /**
     * Returns whether online.
     * @return the result
     */
    public boolean isOnline() {
        return isOnline;
    }

    /**
     * Sets the online.
     * @param online the online
     */
    public void setOnline(boolean online) {
        isOnline = online;
        if (online) {
            this.lastOnline = System.currentTimeMillis();
        }
    }

    /**
     * Returns the permissions.
     * @return the result
     */
    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     * @param permissions the permissions
     */
    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions != null ? permissions : new HashMap<>();
    }

    /**
     * Returns the joined at.
     * @return the result
     */
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    /**
     * Sets the joined at.
     * @param joinedAt the joined at
     */
    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    // Business methods

    /**
     * Check if resident has a specific permission
     * @param permission Permission node
     * @return True if has permission
     */
    public boolean hasPermission(String permission) {
        return permissions.getOrDefault(permission, false);
    }

    /**
     * Set a permission for this resident
     * @param permission Permission node
     * @param value Permission value
     */
    public void setPermission(String permission, boolean value) {
        permissions.put(permission, value);
    }

    /**
     * Check if resident has a guild
     * @return True if has guild
     */
    public boolean hasGuild() {
        return guild != null && !guild.isEmpty();
    }

    /**
     * Check if resident is the mayor of their guild
     * @return True if is mayor (would need guild service to verify)
     */
    public boolean isMayor() {
        // This would typically be checked against the guild service
        // For now, we can check if resident has mayor permission
        return hasPermission("guilds.mayor");
    }

    /**
     * Check if resident is an assistant in their guild
     * @return True if is assistant
     */
    public boolean isAssistant() {
        return hasPermission("guilds.assistant");
    }

    /**
     * Join a guild
     * @param guildName Guild name
     */
    public void joinGuild(String guildName) {
        this.guild = guildName;
        this.lastOnline = System.currentTimeMillis();
    }

    /**
     * Leave current guild
     */
    public void leaveGuild() {
        this.guild = null;
        // Remove guild-specific permissions
        permissions.remove("guilds.mayor");
        permissions.remove("guilds.assistant");
    }

    /**
     * Update last online time to current time
     */
    public void updateLastOnline() {
        this.lastOnline = System.currentTimeMillis();
    }

    /**
     * Get days since last online
     * @return Days since last online
     */
    public long getDaysSinceLastOnline() {
        return (System.currentTimeMillis() - lastOnline) / (1000 * 60 * 60 * 24);
    }

    /**
     * Check if resident is inactive (more than 30 days)
     * @return True if inactive
     */
    public boolean isInactive() {
        return getDaysSinceLastOnline() > 30;
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Resident resident = (Resident) obj;
        return uuid.equals(resident.uuid);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "Resident{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", guild='" + guild + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }
}