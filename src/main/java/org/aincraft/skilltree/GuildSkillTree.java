package org.aincraft.skilltree;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entity class representing a guild's skill tree progression state.
 * Tracks available skill points, total earned points, and unlocked skills.
 * This class is mutable to reflect dynamic progression changes.
 */
public final class GuildSkillTree {
    private final String guildId;
    private int availableSkillPoints;
    private int totalSkillPointsEarned;
    private final Set<String> unlockedSkillIds;

    /**
     * Constructor for creating a new skill tree for a guild.
     * @param guildId the unique identifier of the guild
     */
    public GuildSkillTree(String guildId) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.availableSkillPoints = 0;
        this.totalSkillPointsEarned = 0;
        this.unlockedSkillIds = new HashSet<>();
    }

    /**
     * Constructor for restoring a skill tree from persistent storage.
     * @param guildId the unique identifier of the guild
     * @param availableSP current available skill points
     * @param totalSP total skill points earned across all time
     * @param unlockedSkills set of unlocked skill IDs
     */
    public GuildSkillTree(String guildId, int availableSP, int totalSP, Set<String> unlockedSkills) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.availableSkillPoints = Math.max(0, availableSP);
        this.totalSkillPointsEarned = Math.max(0, totalSP);
        this.unlockedSkillIds = unlockedSkills != null ? new HashSet<>(unlockedSkills) : new HashSet<>();
    }

    /**
     * Gets the guild ID this skill tree belongs to.
     * @return the guild identifier
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Gets the number of skill points currently available to spend.
     * @return available skill points
     */
    public int getAvailableSkillPoints() {
        return availableSkillPoints;
    }

    /**
     * Gets the total number of skill points earned by this guild across all time.
     * @return total skill points earned
     */
    public int getTotalSkillPointsEarned() {
        return totalSkillPointsEarned;
    }

    /**
     * Gets an unmodifiable view of all unlocked skill IDs.
     * @return set of unlocked skill IDs
     */
    public Set<String> getUnlockedSkillIds() {
        return Collections.unmodifiableSet(unlockedSkillIds);
    }

    /**
     * Checks if a specific skill has been unlocked.
     * @param skillId the skill ID to check
     * @return true if the skill is unlocked, false otherwise
     */
    public boolean hasSkill(String skillId) {
        return unlockedSkillIds.contains(skillId);
    }

    /**
     * Checks if the guild can afford to unlock a skill with the given cost.
     * @param spCost the skill point cost
     * @return true if affordable, false otherwise
     */
    public boolean canAfford(int spCost) {
        return availableSkillPoints >= spCost;
    }

    /**
     * Unlocks a skill, deducting the skill point cost.
     * @param skillId the skill ID to unlock
     * @param spCost the skill point cost
     * @throws IllegalStateException if insufficient skill points or skill already unlocked
     */
    public void unlockSkill(String skillId, int spCost) {
        if (!canAfford(spCost)) {
            throw new IllegalStateException("Not enough skill points");
        }
        if (hasSkill(skillId)) {
            throw new IllegalStateException("Skill already unlocked");
        }
        unlockedSkillIds.add(skillId);
        availableSkillPoints -= spCost;
    }

    /**
     * Awards skill points to the guild.
     * @param amount the number of skill points to award
     * @throws IllegalArgumentException if amount is not positive
     */
    public void awardSkillPoints(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        availableSkillPoints += amount;
        totalSkillPointsEarned += amount;
    }

    /**
     * Resets the skill tree, clearing all unlocked skills and returning points to available pool.
     * Total points earned remains unchanged.
     */
    public void respec() {
        unlockedSkillIds.clear();
        availableSkillPoints = totalSkillPointsEarned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildSkillTree that)) return false;
        return Objects.equals(guildId, that.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId);
    }

    @Override
    public String toString() {
        return "GuildSkillTree{" +
                "guildId='" + guildId + '\'' +
                ", availableSkillPoints=" + availableSkillPoints +
                ", totalSkillPointsEarned=" + totalSkillPointsEarned +
                ", unlockedSkillIds=" + unlockedSkillIds +
                '}';
    }
}
