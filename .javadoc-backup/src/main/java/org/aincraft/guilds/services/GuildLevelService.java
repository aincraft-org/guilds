package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildLevel;

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
        private final boolean eligible;
        private final String reason;
        private final Map<String, Integer> requiredResources;
        private final Map<String, Integer> contributedResources;
        private final Map<String, Boolean> resourceStatus;

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

        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
        public Map<String, Integer> getRequiredResources() { return requiredResources; }
        public Map<String, Integer> getContributedResources() { return contributedResources; }
        public Map<String, Boolean> getResourceStatus() { return resourceStatus; }
    }

    /**
     * Result of a guild upgrade operation
     */
    class UpgradeResult {
        private final boolean successful;
        private final String message;
        private final int previousLevel;
        private final int newLevel;
        private final int techPointsEarned;

        public UpgradeResult(boolean successful, String message, int previousLevel, int newLevel, int techPointsEarned) {
            this.successful = successful;
            this.message = message;
            this.previousLevel = previousLevel;
            this.newLevel = newLevel;
            this.techPointsEarned = techPointsEarned;
        }

        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
        public int getPreviousLevel() { return previousLevel; }
        public int getNewLevel() { return newLevel; }
        public int getTechPointsEarned() { return techPointsEarned; }
    }

    /**
     * Level benefits information
     */
    class LevelBenefits {
        private final int claimLimitBonus;
        private final int assistantSlotsBonus;
        private final double dailyIncomeBonus;
        private final int techPointsReward;
        private final List<String> unlockedPlotTypes;

        public LevelBenefits(int claimLimitBonus, int assistantSlotsBonus,
                            double dailyIncomeBonus, int techPointsReward,
                            List<String> unlockedPlotTypes) {
            this.claimLimitBonus = claimLimitBonus;
            this.assistantSlotsBonus = assistantSlotsBonus;
            this.dailyIncomeBonus = dailyIncomeBonus;
            this.techPointsReward = techPointsReward;
            this.unlockedPlotTypes = unlockedPlotTypes != null ? unlockedPlotTypes : List.of();
        }

        public int getClaimLimitBonus() { return claimLimitBonus; }
        public int getAssistantSlotsBonus() { return assistantSlotsBonus; }
        public double getDailyIncomeBonus() { return dailyIncomeBonus; }
        public int getTechPointsReward() { return techPointsReward; }
        public List<String> getUnlockedPlotTypes() { return unlockedPlotTypes; }
    }
}