package org.aincraft.guilds.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates town level system data and logic
 * Extracted from Town.java to follow Single Responsibility Principle
 */
public class TownLevelData {

    private int level;
    private int techPoints;
    private Map<String, Integer> upgradeProgress;

    /**
     * Default constructor
     */
    public TownLevelData() {
        this.level = 1;
        this.techPoints = 0;
        this.upgradeProgress = new HashMap<>();
    }

    /**
     * Constructor with initial values
     * @param level Initial town level
     * @param techPoints Initial tech points
     * @param upgradeProgress Initial upgrade progress
     */
    public TownLevelData(int level, int techPoints, Map<String, Integer> upgradeProgress) {
        this.level = Math.max(1, level);
        this.techPoints = Math.max(0, techPoints);
        this.upgradeProgress = upgradeProgress != null ? new HashMap<>(upgradeProgress) : new HashMap<>();
    }

    // Getters and Setters

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getTechPoints() {
        return techPoints;
    }

    public void setTechPoints(int techPoints) {
        this.techPoints = Math.max(0, techPoints);
    }

    public Map<String, Integer> getUpgradeProgress() {
        return upgradeProgress;
    }

    public void setUpgradeProgress(Map<String, Integer> upgradeProgress) {
        this.upgradeProgress = upgradeProgress != null ? new HashMap<>(upgradeProgress) : new HashMap<>();
    }

    // Business methods

    /**
     * Add tech points to the town
     * @param points Tech points to add
     */
    public void addTechPoints(int points) {
        this.techPoints += points;
    }

    /**
     * Check if town can afford an upgrade to the next level
     * @param nextLevelRequirements Resource requirements for next level
     * @return Map of resource affordability
     */
    public Map<String, Boolean> canAffordUpgrade(Map<String, Integer> nextLevelRequirements) {
        Map<String, Boolean> affordability = new HashMap<>();
        for (Map.Entry<String, Integer> entry : nextLevelRequirements.entrySet()) {
            String resourceType = entry.getKey();
            int required = entry.getValue();
            int contributed = upgradeProgress.getOrDefault(resourceType, 0);
            affordability.put(resourceType, contributed >= required);
        }
        return affordability;
    }

    /**
     * Check if all requirements for the next level are met
     * @param nextLevelRequirements Resource requirements for next level
     * @return True if all requirements are met
     */
    public boolean canUpgradeToNextLevel(Map<String, Integer> nextLevelRequirements) {
        if (nextLevelRequirements == null || nextLevelRequirements.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Integer> entry : nextLevelRequirements.entrySet()) {
            String resourceType = entry.getKey();
            int required = entry.getValue();
            int contributed = upgradeProgress.getOrDefault(resourceType, 0);
            if (contributed < required) {
                return false;
            }
        }
        return true;
    }

    /**
     * Contribute resources to town upgrade progress
     * @param resourceType Type of resource
     * @param amount Amount to contribute
     */
    public void contributeToUpgrade(ResourceType resourceType, int amount) {
        if (amount <= 0 || resourceType == null) return;

        String normalizedResource = resourceType.getNormalizedName();
        int currentProgress = upgradeProgress.getOrDefault(normalizedResource, 0);
        upgradeProgress.put(normalizedResource, currentProgress + amount);
    }

    /**
     * Contribute resources to town upgrade progress (string version for backward compatibility)
     * @param resourceTypeStr Type of resource as string
     * @param amount Amount to contribute
     */
    public void contributeToUpgrade(String resourceTypeStr, int amount) {
        ResourceType.fromString(resourceTypeStr)
                .ifPresent(resourceType -> contributeToUpgrade(resourceType, amount));
    }

    /**
     * Get the contribution progress for a specific resource
     * @param resourceType Type of resource
     * @param requiredAmount Required amount for next level
     * @return Progress percentage (0-100)
     */
    public double getResourceProgress(String resourceType, int requiredAmount) {
        if (requiredAmount <= 0) return 100.0;

        String normalizedResource = resourceType.toLowerCase();
        int contributed = upgradeProgress.getOrDefault(normalizedResource, 0);
        return Math.min(100.0, (contributed * 100.0) / requiredAmount);
    }

    /**
     * Calculate overall upgrade progress percentage
     * @param nextLevelRequirements Resource requirements for next level
     * @return Overall progress percentage (0-100)
     */
    public double getOverallUpgradeProgress(Map<String, Integer> nextLevelRequirements) {
        if (nextLevelRequirements == null || nextLevelRequirements.isEmpty()) {
            return 0.0;
        }

        double totalProgress = 0.0;
        int resourceCount = 0;

        for (Map.Entry<String, Integer> entry : nextLevelRequirements.entrySet()) {
            String resourceType = entry.getKey();
            int required = entry.getValue();
            if (required > 0) {
                totalProgress += getResourceProgress(resourceType, required);
                resourceCount++;
            }
        }

        return resourceCount > 0 ? totalProgress / resourceCount : 0.0;
    }

    /**
     * Reset upgrade progress for next level preparation
     */
    public void resetUpgradeProgress() {
        this.upgradeProgress.clear();
    }

    /**
     * Level up the town to the next level
     * @param newLevel New level to set
     * @param techPointsReward Tech points to add for this level
     */
    public void levelUp(int newLevel, int techPointsReward) {
        if (newLevel > this.level) {
            this.level = newLevel;
            this.addTechPoints(techPointsReward);
            this.resetUpgradeProgress();
        }
    }

    /**
     * Get the maximum number of assistant slots based on town level
     * @return Maximum assistant slots
     */
    public int getMaxAssistantSlots() {
        // Base: 1 assistant + 1 per 10 levels
        return 1 + (level - 1) / 10;
    }

    /**
     * Get the maximum claim limit based on town level
     * @return Maximum claim limit in chunks
     */
    public int getMaxClaimLimit() {
        // Base: 5 chunks + 2 per level
        return 5 + (level - 1) * 2;
    }

    /**
     * Get daily income bonus based on town level
     * @return Daily income bonus
     */
    public double getDailyIncomeBonus() {
        // Quadratic scaling: 0.5 * level^2
        return 0.5 * level * level;
    }

    /**
     * Check if the town has reached its assistant limit
     * @param currentAssistants Current number of assistants
     * @return True if at assistant limit
     */
    public boolean isAtAssistantLimit(int currentAssistants) {
        return currentAssistants >= getMaxAssistantSlots();
    }

    /**
     * Check if the town has reached its claim limit
     * @param currentClaims Current number of claimed chunks
     * @return True if at claim limit
     */
    public boolean isAtClaimLimit(int currentClaims) {
        return currentClaims >= getMaxClaimLimit();
    }

    @Override
    public String toString() {
        return "TownLevelData{" +
                "level=" + level +
                ", techPoints=" + techPoints +
                ", upgradeProgress=" + upgradeProgress +
                '}';
    }
}
