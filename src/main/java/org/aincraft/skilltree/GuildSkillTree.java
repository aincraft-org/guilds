package org.aincraft.skilltree;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable state entity representing a guild's skill tree progression.
 * Tracks available skill points, lifetime earnings, and unlocked skills.
 * Single Responsibility: Guild skill tree state management.
 */
public final class GuildSkillTree {
    private static final int MIN_SP = 0;

    private final UUID guildId;
    private int availableSp;
    private int totalSpEarned;
    private final Set<String> unlockedSkills;

    /**
     * Creates a new skill tree for a guild with no skills unlocked.
     *
     * @param guildId the guild ID (cannot be null)
     */
    public GuildSkillTree(UUID guildId) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.availableSp = 0;
        this.totalSpEarned = 0;
        this.unlockedSkills = new HashSet<>();
    }

    /**
     * Creates a skill tree with existing data (for database restoration).
     *
     * @param guildId the guild ID
     * @param availableSp available skill points
     * @param totalSpEarned total skill points earned lifetime
     * @param unlockedSkills set of unlocked skill IDs
     */
    public GuildSkillTree(UUID guildId, int availableSp, int totalSpEarned, Set<String> unlockedSkills) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.availableSp = Math.max(availableSp, MIN_SP);
        this.totalSpEarned = Math.max(totalSpEarned, 0);
        this.unlockedSkills = unlockedSkills != null ? new HashSet<>(unlockedSkills) : new HashSet<>();
    }

    /**
     * Awards skill points to this guild.
     *
     * @param amount the amount of skill points to award (must be positive)
     * @throws IllegalArgumentException if amount is negative
     */
    public void awardSkillPoints(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Skill points cannot be negative");
        }
        this.availableSp += amount;
        this.totalSpEarned += amount;
    }

    /**
     * Checks if a skill can be unlocked given current state and prerequisites.
     *
     * @param skill the skill to check
     * @return true if all prerequisites are unlocked and SP is sufficient
     */
    public boolean canUnlock(SkillDefinition skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");

        // Check SP cost
        if (availableSp < skill.spCost()) {
            return false;
        }

        // Check prerequisites
        if (skill.hasPrerequisites()) {
            for (String prereqId : skill.prerequisites()) {
                if (!unlockedSkills.contains(prereqId)) {
                    return false;
                }
            }
        }

        // Already unlocked
        if (unlockedSkills.contains(skill.id())) {
            return false;
        }

        return true;
    }

    /**
     * Unlocks a skill, consuming the required skill points.
     * Should only be called after canUnlock() returns true.
     *
     * @param skill the skill to unlock
     * @throws IllegalStateException if prerequisites are not met or insufficient SP
     */
    public void unlockSkill(SkillDefinition skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");

        if (!canUnlock(skill)) {
            throw new IllegalStateException("Cannot unlock skill: " + skill.id());
        }

        availableSp -= skill.spCost();
        unlockedSkills.add(skill.id());
    }

    /**
     * Resets the skill tree, refunding all spent skill points.
     * Clears all unlocked skills and restores available SP.
     */
    public void respec() {
        this.availableSp += calculateSpent();
        this.unlockedSkills.clear();
    }

    /**
     * Calculates the number of skill points currently spent.
     *
     * @return total SP spent on unlocked skills
     */
    private int calculateSpent() {
        // Note: This requires access to skill registry to calculate actual costs
        // For now, return the difference between total earned and available
        return Math.max(totalSpEarned - availableSp, 0);
    }

    /**
     * Checks if a skill is currently unlocked.
     *
     * @param skillId the skill ID
     * @return true if the skill is in the unlocked set
     */
    public boolean isUnlocked(String skillId) {
        return unlockedSkills.contains(skillId);
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
     * Gets available skill points.
     *
     * @return available SP
     */
    public int getAvailableSp() {
        return availableSp;
    }

    /**
     * Gets total skill points earned (lifetime).
     *
     * @return total SP earned
     */
    public int getTotalSpEarned() {
        return totalSpEarned;
    }

    /**
     * Gets an unmodifiable view of unlocked skills.
     *
     * @return unmodifiable set of unlocked skill IDs
     */
    public Set<String> getUnlockedSkills() {
        return Collections.unmodifiableSet(unlockedSkills);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildSkillTree)) return false;
        GuildSkillTree that = (GuildSkillTree) o;
        return Objects.equals(guildId, that.guildId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId);
    }

    @Override
    public String toString() {
        return "GuildSkillTree{" +
                "guildId=" + guildId +
                ", availableSp=" + availableSp +
                ", totalSpEarned=" + totalSpEarned +
                ", unlockedSkills=" + unlockedSkills.size() +
                '}';
    }
}
