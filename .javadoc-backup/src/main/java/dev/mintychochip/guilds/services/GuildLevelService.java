package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for guild level system operations
 */
public interface GuildLevelService {

    /**
     * Get a guild level definition by level number
     * @param level Level number
     * @return Guild level definition if found
     */
    Optional<GuildLevel> getGuildLevel(int level);

    /**
     * Get the level definition for the next level of a guild
     * @param guild Guild to get next level for
     * @return Next level definition if available
     */
    Optional<GuildLevel> getNextGuildLevel(Guild guild);

    /**
     * Get all guild level definitions
     * @return List of all guild levels
     */
    List<GuildLevel> getAllGuildLevels();

    /**
     * Get a range of guild levels
     * @param startLevel Starting level
     * @param endLevel Ending level
     * @return List of guild levels in the specified range
     */
    List<GuildLevel> getGuildLevelsInRange(int startLevel, int endLevel);

    /**
     * Check if a guild can upgrade to the next level
     * @param guild Guild to check
     * @return Upgrade eligibility result
     */
    UpgradeEligibility checkUpgradeEligibility(Guild guild);

    /**
     * Process a guild level upgrade
     * @param guild Guild to upgrade
     * @return Upgrade result
     */
    UpgradeResult performGuildUpgrade(Guild guild);

    /**
     * Calculate the total resources contributed by a guild for the next level
     * @param guild Guild to calculate for
     * @return Map of resource types to contributed amounts
     */
    Map<String, Integer> calculateTotalContributions(Guild guild);

    /**
     * Calculate the upgrade progress percentage for a guild
     * @param guild Guild to calculate for
     * @return Progress percentage (0-100)
     */
    double calculateUpgradeProgress(Guild guild);

    /**
     * Get the maximum level available in the system
     * @return Maximum level number
     */
    int getMaxLevel();

    /**
     * Check if a guild has reached the maximum level
     * @param guild Guild to check
     * @return True if at maximum level
     */
    boolean isAtMaxLevel(Guild guild);

    /**
     * Get the benefits unlocked at a specific level
     * @param level Level to get benefits for
     * @return Level benefits
     */
    LevelBenefits getLevelBenefits(int level);

    /**
     * Get the current benefits for a guild based on its level
     * @param guild Guild to get benefits for
     * @return Current guild benefits
     */
    LevelBenefits getCurrentGuildBenefits(Guild guild);

    /**
     * Calculate the total tech points a guild should have based on its level
     * @param guild Guild to calculate for
     * @return Total tech points
     */
    int calculateTotalTechPoints(Guild guild);

    /**
     * Synchronize guild level data with the database
     * @param guild Guild to synchronize
     */
    void syncGuildLevelData(Guild guild);

    /**
     * Reset all guild level data (for testing purposes)
     */
    void resetAllGuildLevelData();

    /**
     * Reload level definitions from config and sync to database
     */
    void reloadLevelDefinitions();

    /**
     * Sync configuration-defined guild levels to database
     */
    void syncConfigToDatabase();

    /**
     * Result of an upgrade eligibility check
     */
    class UpgradeEligibility {
        /** The eligible. */
        private final boolean eligible;
        /** The reason. */
        private final String reason;
        /** The required resources. */
        private final Map<String, Integer> requiredResources;
        /** The contributed resources. */
        private final Map<String, Integer> contributedResources;
        /** The resource status. */
        private final Map<String, Boolean> resourceStatus;

        /**
         * Creates a new upgrade eligibility instance.
         * @param eligible the eligible
         * @param reason the reason
         * @param requiredResources the required resources
         * @param contributedResources the contributed resources
         * @param resourceStatus the resource status
         */
        public UpgradeEligibility(boolean eligible, String reason,
                                Map<String, Integer> requiredResources,
                                Map<String, Integer> contributedResources,
                                Map<String, Boolean> resourceStatus) {
            this.eligible = eligible;
            this.reason = reason;
            this.requiredResources = requiredResources;
            this.contributedResources = contributedResources;
            this.resourceStatus = resourceStatus;
        }

        /**
         * Returns whether eligible.
         * @return the result
         */
        public boolean isEligible() { return eligible; }
        /**
         * Returns the reason.
         * @return the result
         */
        public String getReason() { return reason; }
        /**
         * Returns the required resources.
         * @return the result
         */
        public Map<String, Integer> getRequiredResources() { return requiredResources; }
        /**
         * Returns the contributed resources.
         * @return the result
         */
        public Map<String, Integer> getContributedResources() { return contributedResources; }
        /**
         * Returns the resource status.
         * @return the result
         */
        public Map<String, Boolean> getResourceStatus() { return resourceStatus; }
    }

    /**
     * Result of a guild upgrade operation
     */
    class UpgradeResult {
        /** The successful. */
        private final boolean successful;
        /** The message. */
        private final String message;
        /** The previous level. */
        private final int previousLevel;
        /** The new level. */
        private final int newLevel;
        /** The tech points earned. */
        private final int techPointsEarned;

        /**
         * Creates a new upgrade result instance.
         * @param successful the successful
         * @param message the message
         * @param previousLevel the previous level
         * @param newLevel the new level
         * @param techPointsEarned the tech points earned
         */
        public UpgradeResult(boolean successful, String message, int previousLevel, int newLevel, int techPointsEarned) {
            this.successful = successful;
            this.message = message;
            this.previousLevel = previousLevel;
            this.newLevel = newLevel;
            this.techPointsEarned = techPointsEarned;
        }

        /**
         * Returns whether successful.
         * @return the result
         */
        public boolean isSuccessful() { return successful; }
        /**
         * Returns the message.
         * @return the result
         */
        public String getMessage() { return message; }
        /**
         * Returns the previous level.
         * @return the result
         */
        public int getPreviousLevel() { return previousLevel; }
        /**
         * Returns the new level.
         * @return the result
         */
        public int getNewLevel() { return newLevel; }
        /**
         * Returns the tech points earned.
         * @return the result
         */
        public int getTechPointsEarned() { return techPointsEarned; }
    }

    /**
     * Level benefits information
     */
    class LevelBenefits {
        /** The claim limit bonus. */
        private final int claimLimitBonus;
        /** The assistant slots bonus. */
        private final int assistantSlotsBonus;
        /** The daily income bonus. */
        private final double dailyIncomeBonus;
        /** The tech points reward. */
        private final int techPointsReward;
        /** The unlocked plot types. */
        private final List<String> unlockedPlotTypes;

        /**
         * Creates a new level benefits instance.
         * @param claimLimitBonus the claim limit bonus
         * @param assistantSlotsBonus the assistant slots bonus
         * @param dailyIncomeBonus the daily income bonus
         * @param techPointsReward the tech points reward
         * @param unlockedPlotTypes the unlocked plot types
         */
        public LevelBenefits(int claimLimitBonus, int assistantSlotsBonus,
                            double dailyIncomeBonus, int techPointsReward,
                            List<String> unlockedPlotTypes) {
            this.claimLimitBonus = claimLimitBonus;
            this.assistantSlotsBonus = assistantSlotsBonus;
            this.dailyIncomeBonus = dailyIncomeBonus;
            this.techPointsReward = techPointsReward;
            this.unlockedPlotTypes = unlockedPlotTypes != null ? unlockedPlotTypes : List.of();
        }

        /**
         * Returns the claim limit bonus.
         * @return the result
         */
        public int getClaimLimitBonus() { return claimLimitBonus; }
        /**
         * Returns the assistant slots bonus.
         * @return the result
         */
        public int getAssistantSlotsBonus() { return assistantSlotsBonus; }
        /**
         * Returns the daily income bonus.
         * @return the result
         */
        public double getDailyIncomeBonus() { return dailyIncomeBonus; }
        /**
         * Returns the tech points reward.
         * @return the result
         */
        public int getTechPointsReward() { return techPointsReward; }
        /**
         * Returns the unlocked plot types.
         * @return the result
         */
        public List<String> getUnlockedPlotTypes() { return unlockedPlotTypes; }
    }
}