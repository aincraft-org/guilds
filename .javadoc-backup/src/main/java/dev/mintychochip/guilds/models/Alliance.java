package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a alliance in the Guilds system
 */
public class Alliance {

    /** The id. */
    private String id;
    /** The name. */
    private String name;
    /** The capital guild id. */
    private String capitalGuildId;
    /** The member guild ids. */
    private Set<String> memberGuildIds;
    /** The king uuid. */
    private UUID kingUuid;
    /** The ministers. */
    private Set<UUID> ministers;
    /** The allies. */
    private Set<String> allies;
    /** The enemies. */
    private Set<String> enemies;
    /** The tax rate. */
    private double taxRate;
    /** The is open. */
    private boolean isOpen;
    /** The created at. */
    private LocalDateTime createdAt;

    /**
     * Default constructor for database mapping
     */
    public Alliance() {
        this.memberGuildIds = new HashSet<>();
        this.ministers = new HashSet<>();
        this.allies = new HashSet<>();
        this.enemies = new HashSet<>();
        this.taxRate = 0.0;
        this.isOpen = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new alliance
     * @param name Alliance name
     * @param capitalGuildId Capital guild ID
     * @param kingUuid King's UUID
     */
    public Alliance(String name, String capitalGuildId, UUID kingUuid) {
        this();
        this.name = name;
        this.id = UUID.randomUUID().toString();
        this.capitalGuildId = capitalGuildId;
        this.kingUuid = kingUuid;
        this.memberGuildIds.add(capitalGuildId);
    }

    // Getters and Setters
    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
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
     * Returns the capital guild id.
     * @return the result
     */
    public String getCapitalGuildId() {
        return capitalGuildId;
    }

    /**
     * Sets the capital guild id.
     * @param capitalGuildId the capital guild id
     */
    public void setCapitalGuildId(String capitalGuildId) {
        this.capitalGuildId = capitalGuildId;
    }

    /**
     * Returns the member guild ids.
     * @return the result
     */
    public Set<String> getMemberGuildIds() {
        return memberGuildIds;
    }

    /**
     * Sets the member guild ids.
     * @param memberGuildIds the member guild ids
     */
    public void setMemberGuildIds(Set<String> memberGuildIds) {
        this.memberGuildIds = memberGuildIds != null ? memberGuildIds : new HashSet<>();
    }

    /**
     * Returns the king uuid.
     * @return the result
     */
    public UUID getKingUuid() {
        return kingUuid;
    }

    /**
     * Sets the king uuid.
     * @param kingUuid the king uuid
     */
    public void setKingUuid(UUID kingUuid) {
        this.kingUuid = kingUuid;
    }

    /**
     * Returns the ministers.
     * @return the result
     */
    public Set<UUID> getMinisters() {
        return ministers;
    }

    /**
     * Sets the ministers.
     * @param ministers the ministers
     */
    public void setMinisters(Set<UUID> ministers) {
        this.ministers = ministers != null ? ministers : new HashSet<>();
    }

    /**
     * Returns the allies.
     * @return the result
     */
    public Set<String> getAllies() {
        return allies;
    }

    /**
     * Sets the allies.
     * @param allies the allies
     */
    public void setAllies(Set<String> allies) {
        this.allies = allies != null ? allies : new HashSet<>();
    }

    /**
     * Returns the enemies.
     * @return the result
     */
    public Set<String> getEnemies() {
        return enemies;
    }

    /**
     * Sets the enemies.
     * @param enemies the enemies
     */
    public void setEnemies(Set<String> enemies) {
        this.enemies = enemies != null ? enemies : new HashSet<>();
    }

    /**
     * Returns the tax rate.
     * @return the result
     */
    public double getTaxRate() {
        return taxRate;
    }

    /**
     * Sets the tax rate.
     * @param taxRate the tax rate
     */
    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0.0, Math.min(taxRate, 100.0)); // Ensure range 0-100%
    }

    /**
     * Returns whether open.
     * @return the result
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Sets the open.
     * @param open the open
     */
    public void setOpen(boolean open) {
        isOpen = open;
    }

    /**
     * Returns the created at.
     * @return the result
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created at.
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Business methods

    /**
     * Add a guild to the alliance
     * @param guildId Guild ID to add
     * @return True if added successfully, false if already a member
     */
    public boolean addGuild(String guildId) {
        return memberGuildIds.add(guildId);
    }

    /**
     * Remove a guild from the alliance
     * @param guildId Guild ID to remove
     * @return True if removed successfully, false if guild was not a member
     */
    public boolean removeGuild(String guildId) {
        // Cannot remove capital guild
        if (capitalGuildId.equals(guildId)) {
            return false;
        }
        return memberGuildIds.remove(guildId);
    }

    /**
     * Check if a guild is a member of this alliance
     * @param guildId Guild ID to check
     * @return True if guild is a member
     */
    public boolean hasGuild(String guildId) {
        return memberGuildIds.contains(guildId);
    }

    /**
     * Get the number of guilds in this alliance
     * @return Guild count
     */
    public int getGuildCount() {
        return memberGuildIds.size();
    }

    /**
     * Add a minister to the alliance
     * @param ministerUuid Minister UUID to add
     * @return True if added successfully, false if already a minister
     */
    public boolean addMinister(UUID ministerUuid) {
        return ministers.add(ministerUuid);
    }

    /**
     * Remove a minister from the alliance
     * @param ministerUuid Minister UUID to remove
     * @return True if removed successfully, false if player was not a minister
     */
    public boolean removeMinister(UUID ministerUuid) {
        return ministers.remove(ministerUuid);
    }

    /**
     * Check if a player is a minister
     * @param playerUuid Player UUID to check
     * @return True if player is a minister
     */
    public boolean isMinister(UUID playerUuid) {
        return ministers.contains(playerUuid);
    }

    /**
     * Check if a player is the king
     * @param playerUuid Player UUID to check
     * @return True if player is the king
     */
    public boolean isKing(UUID playerUuid) {
        return kingUuid != null && kingUuid.equals(playerUuid);
    }

    /**
     * Check if a player has authority (king or minister)
     * @param playerUuid Player UUID to check
     * @return True if player has authority
     */
    public boolean hasAuthority(UUID playerUuid) {
        return isKing(playerUuid) || isMinister(playerUuid);
    }

    /**
     * Add an alliance as an ally
     * @param allianceName Alliance name to add as ally
     * @return True if added successfully, false if already an ally
     */
    public boolean addAlly(String allianceName) {
        return allies.add(allianceName);
    }

    /**
     * Remove an alliance from allies
     * @param allianceName Alliance name to remove as ally
     * @return True if removed successfully, false if alliance was not an ally
     */
    public boolean removeAlly(String allianceName) {
        return allies.remove(allianceName);
    }

    /**
     * Check if an alliance is an ally
     * @param allianceName Alliance name to check
     * @return True if alliance is an ally
     */
    public boolean isAlly(String allianceName) {
        return allies.contains(allianceName);
    }

    /**
     * Add an alliance as an enemy
     * @param allianceName Alliance name to add as enemy
     * @return True if added successfully, false if already an enemy
     */
    public boolean addEnemy(String allianceName) {
        return enemies.add(allianceName);
    }

    /**
     * Remove an alliance from enemies
     * @param allianceName Alliance name to remove as enemy
     * @return True if removed successfully, false if alliance was not an enemy
     */
    public boolean removeEnemy(String allianceName) {
        return enemies.remove(allianceName);
    }

    /**
     * Check if an alliance is an enemy
     * @param allianceName Alliance name to check
     * @return True if alliance is an enemy
     */
    public boolean isEnemy(String allianceName) {
        return enemies.contains(allianceName);
    }

    /**
     * Get the relationship with another alliance
     * @param otherAlliance Alliance name to check relationship with
     * @return Relationship type (ALLY, ENEMY, NEUTRAL)
     */
    public AllianceRelation getRelation(String otherAlliance) {
        if (isAlly(otherAlliance)) {
            return AllianceRelation.ALLY;
        } else if (isEnemy(otherAlliance)) {
            return AllianceRelation.ENEMY;
        }
        return AllianceRelation.NEUTRAL;
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Alliance alliance = (Alliance) obj;
        return id.equals(alliance.id);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "Alliance{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", capitalGuildId='" + capitalGuildId + '\'' +
                ", guildCount=" + getGuildCount() +
                ", kingUuid=" + kingUuid +
                ", ministerCount=" + ministers.size() +
                ", allyCount=" + allies.size() +
                ", enemyCount=" + enemies.size() +
                ", taxRate=" + taxRate +
                ", isOpen=" + isOpen +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Enum representing possible alliance relationships
     */
    public enum AllianceRelation {
        /** The ally constant. */
        ALLY,
        /** The enemy constant. */
        ENEMY,
        /** The neutral constant. */
        NEUTRAL
    }
}