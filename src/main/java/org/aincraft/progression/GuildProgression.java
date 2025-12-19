package org.aincraft.progression;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the progression state of a guild.
 * Tracks level, XP progress, and level-up history.
 * Single Responsibility: Guild progression state management.
 */
public final class GuildProgression {
    private static final int MIN_LEVEL = 1;

    private final UUID guildId;
    private int level;
    private long currentXp;
    private long totalXpEarned;
    private Long lastLevelupTime;

    /**
     * Creates a new guild progression state.
     *
     * @param guildId the guild ID (cannot be null)
     */
    public GuildProgression(UUID guildId) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.level = MIN_LEVEL;
        this.currentXp = 0;
        this.totalXpEarned = 0;
        this.lastLevelupTime = null;
    }

    /**
     * Creates a guild progression state with existing data (for database restoration).
     *
     * @param guildId the guild ID
     * @param level the current level
     * @param currentXp the current XP towards next level
     * @param totalXpEarned the total XP earned all-time
     * @param lastLevelupTime the timestamp of last level-up (can be null)
     */
    public GuildProgression(UUID guildId, int level, long currentXp, long totalXpEarned, Long lastLevelupTime) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.level = Math.max(level, MIN_LEVEL);
        this.currentXp = Math.max(currentXp, 0);
        this.totalXpEarned = Math.max(totalXpEarned, 0);
        this.lastLevelupTime = lastLevelupTime;
    }

    /**
     * Adds XP to the guild's progression.
     *
     * @param amount the amount of XP to add (must be positive)
     * @throws IllegalArgumentException if amount is negative
     */
    public void addXp(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("XP amount cannot be negative");
        }
        this.currentXp += amount;
        this.totalXpEarned += amount;
    }

    /**
     * Checks if the guild can level up with the given required XP.
     *
     * @param requiredXp the XP required to level up
     * @return true if current XP meets or exceeds requirement
     */
    public boolean canLevelUp(long requiredXp) {
        return currentXp >= requiredXp;
    }

    /**
     * Levels up the guild, consuming current XP and recording timestamp.
     * Excess XP carries over to next level.
     *
     * @param requiredXp the XP cost for this level-up
     * @throws IllegalStateException if insufficient XP
     */
    public void levelUp(long requiredXp) {
        if (!canLevelUp(requiredXp)) {
            throw new IllegalStateException("Insufficient XP to level up");
        }
        this.currentXp -= requiredXp;
        this.level++;
        this.lastLevelupTime = System.currentTimeMillis();
    }

    /**
     * Gets the guild ID.
     *
     * @return the guild ID
     */
    public UUID getGuildId() {
        return guildId;
    }

    /**
     * Gets the current level.
     *
     * @return the level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the current XP towards next level.
     *
     * @return the current XP
     */
    public long getCurrentXp() {
        return currentXp;
    }

    /**
     * Gets the total XP earned all-time.
     *
     * @return the total XP earned
     */
    public long getTotalXpEarned() {
        return totalXpEarned;
    }

    /**
     * Gets the timestamp of the last level-up.
     *
     * @return the last level-up timestamp, or null if never leveled up
     */
    public Long getLastLevelupTime() {
        return lastLevelupTime;
    }

    /**
     * Sets the level directly (admin command).
     *
     * @param level the new level
     * @throws IllegalArgumentException if level is less than minimum
     */
    public void setLevel(int level) {
        if (level < MIN_LEVEL) {
            throw new IllegalArgumentException("Level cannot be less than " + MIN_LEVEL);
        }
        this.level = level;
    }

    /**
     * Sets the current XP directly (admin command).
     *
     * @param xp the new XP amount
     * @throws IllegalArgumentException if XP is negative
     */
    public void setCurrentXp(long xp) {
        if (xp < 0) {
            throw new IllegalArgumentException("XP cannot be negative");
        }
        this.currentXp = xp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildProgression)) return false;
        GuildProgression that = (GuildProgression) o;
        return Objects.equals(guildId, that.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId);
    }

    @Override
    public String toString() {
        return "GuildProgression{" +
                "guildId='" + guildId + '\'' +
                ", level=" + level +
                ", currentXp=" + currentXp +
                ", totalXpEarned=" + totalXpEarned +
                '}';
    }
}
