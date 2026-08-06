package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a player (resident) in the Guilds system
 */
public class  Resident {

    private UUID uuid;
    private String name;
    private String town;
    private long lastOnline;
    private boolean isOnline;
    private Map<String, Boolean> permissions;
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
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public long getLastOnline() {
        return lastOnline;
    }

    public void setLastOnline(long lastOnline) {
        this.lastOnline = lastOnline;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
        if (online) {
            this.lastOnline = System.currentTimeMillis();
        }
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions != null ? permissions : new HashMap<>();
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

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
     * Check if resident has a town
     * @return True if has town
     */
    public boolean hasTown() {
        return town != null && !town.isEmpty();
    }

    /**
     * Check if resident is the mayor of their town
     * @return True if is mayor (would need town service to verify)
     */
    public boolean isMayor() {
        // This would typically be checked against the town service
        // For now, we can check if resident has mayor permission
        return hasPermission("guilds.mayor");
    }

    /**
     * Check if resident is an assistant in their town
     * @return True if is assistant
     */
    public boolean isAssistant() {
        return hasPermission("guilds.assistant");
    }

    /**
     * Join a town
     * @param townName Town name
     */
    public void joinTown(String townName) {
        this.town = townName;
        this.lastOnline = System.currentTimeMillis();
    }

    /**
     * Leave current town
     */
    public void leaveTown() {
        this.town = null;
        // Remove town-specific permissions
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Resident resident = (Resident) obj;
        return uuid.equals(resident.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "Resident{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", town='" + town + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }
}