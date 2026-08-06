package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a nation in the Guilds system
 */
public class Nation {

    private String id;
    private String name;
    private String capitalGuildId;
    private Set<String> memberGuildIds;
    private UUID kingUuid;
    private Set<UUID> ministers;
    private Set<String> alliances;
    private Set<String> enemies;
    private double taxRate;
    private boolean isOpen;
    private LocalDateTime createdAt;

    /**
     * Default constructor for database mapping
     */
    public Nation() {
        this.memberGuildIds = new HashSet<>();
        this.ministers = new HashSet<>();
        this.alliances = new HashSet<>();
        this.enemies = new HashSet<>();
        this.taxRate = 0.0;
        this.isOpen = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new nation
     * @param name Nation name
     * @param capitalGuildId Capital guild ID
     * @param kingUuid King's UUID
     */
    public Nation(String name, String capitalGuildId, UUID kingUuid) {
        this();
        this.name = name;
        this.id = UUID.randomUUID().toString();
        this.capitalGuildId = capitalGuildId;
        this.kingUuid = kingUuid;
        this.memberGuildIds.add(capitalGuildId);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCapitalGuildId() {
        return capitalGuildId;
    }

    public void setCapitalGuildId(String capitalGuildId) {
        this.capitalGuildId = capitalGuildId;
    }

    public Set<String> getMemberGuildIds() {
        return memberGuildIds;
    }

    public void setMemberGuildIds(Set<String> memberGuildIds) {
        this.memberGuildIds = memberGuildIds != null ? memberGuildIds : new HashSet<>();
    }

    public UUID getKingUuid() {
        return kingUuid;
    }

    public void setKingUuid(UUID kingUuid) {
        this.kingUuid = kingUuid;
    }

    public Set<UUID> getMinisters() {
        return ministers;
    }

    public void setMinisters(Set<UUID> ministers) {
        this.ministers = ministers != null ? ministers : new HashSet<>();
    }

    public Set<String> getAlliances() {
        return alliances;
    }

    public void setAlliances(Set<String> alliances) {
        this.alliances = alliances != null ? alliances : new HashSet<>();
    }

    public Set<String> getEnemies() {
        return enemies;
    }

    public void setEnemies(Set<String> enemies) {
        this.enemies = enemies != null ? enemies : new HashSet<>();
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0.0, Math.min(taxRate, 100.0)); // Ensure range 0-100%
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Business methods

    /**
     * Add a guild to the nation
     * @param guildId Guild ID to add
     * @return True if added successfully, false if already a member
     */
    public boolean addGuild(String guildId) {
        return memberGuildIds.add(guildId);
    }

    /**
     * Remove a guild from the nation
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
     * Check if a guild is a member of this nation
     * @param guildId Guild ID to check
     * @return True if guild is a member
     */
    public boolean hasGuild(String guildId) {
        return memberGuildIds.contains(guildId);
    }

    /**
     * Get the number of guilds in this nation
     * @return Guild count
     */
    public int getGuildCount() {
        return memberGuildIds.size();
    }

    /**
     * Add a minister to the nation
     * @param ministerUuid Minister UUID to add
     * @return True if added successfully, false if already a minister
     */
    public boolean addMinister(UUID ministerUuid) {
        return ministers.add(ministerUuid);
    }

    /**
     * Remove a minister from the nation
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
     * Add a nation as an ally
     * @param nationName Nation name to add as ally
     * @return True if added successfully, false if already an ally
     */
    public boolean addAlly(String nationName) {
        return alliances.add(nationName);
    }

    /**
     * Remove a nation from allies
     * @param nationName Nation name to remove as ally
     * @return True if removed successfully, false if nation was not an ally
     */
    public boolean removeAlly(String nationName) {
        return alliances.remove(nationName);
    }

    /**
     * Check if a nation is an ally
     * @param nationName Nation name to check
     * @return True if nation is an ally
     */
    public boolean isAlly(String nationName) {
        return alliances.contains(nationName);
    }

    /**
     * Add a nation as an enemy
     * @param nationName Nation name to add as enemy
     * @return True if added successfully, false if already an enemy
     */
    public boolean addEnemy(String nationName) {
        return enemies.add(nationName);
    }

    /**
     * Remove a nation from enemies
     * @param nationName Nation name to remove as enemy
     * @return True if removed successfully, false if nation was not an enemy
     */
    public boolean removeEnemy(String nationName) {
        return enemies.remove(nationName);
    }

    /**
     * Check if a nation is an enemy
     * @param nationName Nation name to check
     * @return True if nation is an enemy
     */
    public boolean isEnemy(String nationName) {
        return enemies.contains(nationName);
    }

    /**
     * Get relationship with another nation
     * @param otherNation Nation name to check relationship with
     * @return Relationship type (ALLY, ENEMY, NEUTRAL)
     */
    public NationRelation getRelation(String otherNation) {
        if (isAlly(otherNation)) {
            return NationRelation.ALLY;
        } else if (isEnemy(otherNation)) {
            return NationRelation.ENEMY;
        }
        return NationRelation.NEUTRAL;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Nation nation = (Nation) obj;
        return id.equals(nation.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Nation{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", capitalTownId='" + capitalGuildId + '\'' +
                ", townCount=" + getGuildCount() +
                ", kingUuid=" + kingUuid +
                ", ministerCount=" + ministers.size() +
                ", allyCount=" + alliances.size() +
                ", enemyCount=" + enemies.size() +
                ", taxRate=" + taxRate +
                ", isOpen=" + isOpen +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Enum representing possible nation relationships
     */
    public enum NationRelation {
        ALLY,
        ENEMY,
        NEUTRAL
    }
}