package org.aincraft.towny.services;

import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for town level system operations
 */
public interface TownLevelService {

    /**
     * Get a town level definition by level number
     * @param level Level number
     * @return Town level definition if found
     */
    Optional<TownLevel> getTownLevel(int level);

    /**
     * Get the level definition for the next level of a town
     * @param town Town to get next level for
     * @return Next level definition if available
     */
    Optional<TownLevel> getNextTownLevel(Town town);

    /**
     * Get all town level definitions
     * @return List of all town levels
     */
    List<TownLevel> getAllTownLevels();

    /**
     * Get a range of town levels
     * @param startLevel Starting level
     * @param endLevel Ending level
     * @return List of town levels in the specified range
     */
    List<TownLevel> getTownLevelsInRange(int startLevel, int endLevel);

    /**
     * Check if a town can upgrade to the next level
     * @param town Town to check
     * @return Upgrade eligibility result
     */
    UpgradeEligibility checkUpgradeEligibility(Town town);

    /**
     * Process a town level upgrade
     * @param town Town to upgrade
     * @return Upgrade result
     */
    UpgradeResult performTownUpgrade(Town town);

    /**
     * Calculate the total resources contributed by a town for the next level
     * @param town Town to calculate for
     * @return Map of resource types to contributed amounts
     */
    Map<String, Integer> calculateTotalContributions(Town town);

    /**
     * Calculate the upgrade progress percentage for a town
     * @param town Town to calculate for
     * @return Progress percentage (0-100)
     */
    double calculateUpgradeProgress(Town town);

    /**
     * Get the maximum level available in the system
     * @return Maximum level number
     */
    int getMaxLevel();

    /**
     * Check if a town has reached the maximum level
     * @param town Town to check
     * @return True if at maximum level
     */
    boolean isAtMaxLevel(Town town);

    /**
     * Get the benefits unlocked at a specific level
     * @param level Level to get benefits for
     * @return Level benefits
     */
    LevelBenefits getLevelBenefits(int level);

    /**
     * Get the current benefits for a town based on its level
     * @param town Town to get benefits for
     * @return Current town benefits
     */
    LevelBenefits getCurrentTownBenefits(Town town);

    /**
     * Calculate the total tech points a town should have based on its level
     * @param town Town to calculate for
     * @return Total tech points
     */
    int calculateTotalTechPoints(Town town);

    /**
     * Synchronize town level data with the database
     * @param town Town to synchronize
     */
    void syncTownLevelData(Town town);

    /**
     * Reset all town level data (for testing purposes)
     */
    void resetAllTownLevelData();

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
     * Result of a town upgrade operation
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