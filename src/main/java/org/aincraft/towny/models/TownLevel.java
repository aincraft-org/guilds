package org.aincraft.towny.models;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a town level definition with costs and benefits
 */
public class TownLevel {

    private int level;
    private int diamondCost;
    private int goldCost;
    private int ironCost;
    private int emeraldCost;
    private int experienceCost;
    private int techPointsReward;
    private int claimLimitBonus;
    private int assistantSlotsBonus;
    private double dailyIncomeBonus;
    private List<String> unlockedPlotTypes;
    private LocalDateTime createdAt;

    /**
     * Default constructor for database mapping
     */
    public TownLevel() {
        this.createdAt = LocalDateTime.now();
        this.unlockedPlotTypes = List.of();
    }

    /**
     * Constructor for creating a new town level
     */
    public TownLevel(int level, int diamondCost, int goldCost, int ironCost, int emeraldCost,
                     int experienceCost, int techPointsReward, int claimLimitBonus,
                     int assistantSlotsBonus, double dailyIncomeBonus, List<String> unlockedPlotTypes) {
        this();
        this.level = level;
        this.diamondCost = diamondCost;
        this.goldCost = goldCost;
        this.ironCost = ironCost;
        this.emeraldCost = emeraldCost;
        this.experienceCost = experienceCost;
        this.techPointsReward = techPointsReward;
        this.claimLimitBonus = claimLimitBonus;
        this.assistantSlotsBonus = assistantSlotsBonus;
        this.dailyIncomeBonus = dailyIncomeBonus;
        this.unlockedPlotTypes = unlockedPlotTypes != null ? unlockedPlotTypes : List.of();
    }

    // Getters and Setters
    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getDiamondCost() {
        return diamondCost;
    }

    public void setDiamondCost(int diamondCost) {
        this.diamondCost = Math.max(0, diamondCost);
    }

    public int getGoldCost() {
        return goldCost;
    }

    public void setGoldCost(int goldCost) {
        this.goldCost = Math.max(0, goldCost);
    }

    public int getIronCost() {
        return ironCost;
    }

    public void setIronCost(int ironCost) {
        this.ironCost = Math.max(0, ironCost);
    }

    public int getEmeraldCost() {
        return emeraldCost;
    }

    public void setEmeraldCost(int emeraldCost) {
        this.emeraldCost = Math.max(0, emeraldCost);
    }

    public int getExperienceCost() {
        return experienceCost;
    }

    public void setExperienceCost(int experienceCost) {
        this.experienceCost = Math.max(0, experienceCost);
    }

    public int getTechPointsReward() {
        return techPointsReward;
    }

    public void setTechPointsReward(int techPointsReward) {
        this.techPointsReward = Math.max(0, techPointsReward);
    }

    public int getClaimLimitBonus() {
        return claimLimitBonus;
    }

    public void setClaimLimitBonus(int claimLimitBonus) {
        this.claimLimitBonus = Math.max(0, claimLimitBonus);
    }

    public int getAssistantSlotsBonus() {
        return assistantSlotsBonus;
    }

    public void setAssistantSlotsBonus(int assistantSlotsBonus) {
        this.assistantSlotsBonus = Math.max(0, assistantSlotsBonus);
    }

    public double getDailyIncomeBonus() {
        return dailyIncomeBonus;
    }

    public void setDailyIncomeBonus(double dailyIncomeBonus) {
        this.dailyIncomeBonus = Math.max(0.0, dailyIncomeBonus);
    }

    public List<String> getUnlockedPlotTypes() {
        return unlockedPlotTypes;
    }

    public void setUnlockedPlotTypes(List<String> unlockedPlotTypes) {
        this.unlockedPlotTypes = unlockedPlotTypes != null ? unlockedPlotTypes : List.of();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Business methods

    /**
     * Get the total resource cost as a map
     * @return Map of resource types to their costs
     */
    public java.util.Map<String, Integer> getResourceCosts() {
        java.util.Map<String, Integer> costs = new java.util.HashMap<>();

        if (diamondCost > 0) costs.put(ResourceType.DIAMOND.getNormalizedName(), diamondCost);
        if (goldCost > 0) costs.put(ResourceType.GOLD.getNormalizedName(), goldCost);
        if (ironCost > 0) costs.put(ResourceType.IRON.getNormalizedName(), ironCost);
        if (emeraldCost > 0) costs.put(ResourceType.EMERALD.getNormalizedName(), emeraldCost);
        if (experienceCost > 0) costs.put(ResourceType.EXPERIENCE.getNormalizedName(), experienceCost);

        return costs;
    }

    /**
     * Get the total resource cost as a map with enum keys
     * @return Map of ResourceType enum to their costs
     */
    public java.util.Map<ResourceType, Integer> getResourceCostsEnum() {
        java.util.Map<ResourceType, Integer> costs = new java.util.HashMap<>();

        if (diamondCost > 0) costs.put(ResourceType.DIAMOND, diamondCost);
        if (goldCost > 0) costs.put(ResourceType.GOLD, goldCost);
        if (ironCost > 0) costs.put(ResourceType.IRON, ironCost);
        if (emeraldCost > 0) costs.put(ResourceType.EMERALD, emeraldCost);
        if (experienceCost > 0) costs.put(ResourceType.EXPERIENCE, experienceCost);

        return costs;
    }

    /**
     * Get the cost for a specific resource type
     * @param resourceType Resource type enum
     * @return Cost amount, or 0 if not applicable
     */
    public int getResourceCost(ResourceType resourceType) {
        if (resourceType == null) return 0;

        switch (resourceType) {
            case DIAMOND: return diamondCost;
            case GOLD: return goldCost;
            case IRON: return ironCost;
            case EMERALD: return emeraldCost;
            case EXPERIENCE: return experienceCost;
            default: return 0;
        }
    }

    /**
     * Get the cost for a specific resource type from string (backward compatibility)
     * @param resourceTypeStr Resource type string
     * @return Cost amount, or 0 if not applicable
     */
    public int getResourceCost(String resourceTypeStr) {
        return ResourceType.fromString(resourceTypeStr)
                .map(this::getResourceCost)
                .orElse(0);
    }

    /**
     * Check if this level unlocks a specific plot type
     * @param plotType Plot type to check
     * @return True if the plot type is unlocked at this level
     */
    public boolean unlocksPlotType(String plotType) {
        if (plotType == null || unlockedPlotTypes == null) return false;

        return unlockedPlotTypes.stream()
                .anyMatch(unlocked -> unlocked.equalsIgnoreCase(plotType));
    }

    /**
     * Check if this level has any resource costs
     * @return True if there are resource costs
     */
    public boolean hasResourceCosts() {
        return diamondCost > 0 || goldCost > 0 || ironCost > 0 ||
               emeraldCost > 0 || experienceCost > 0;
    }

    /**
     * Get the total cost in "diamond equivalent" (for comparison purposes)
     * Diamond = 1, Gold = 0.5, Iron = 0.25, Emerald = 2, Experience = 0.1
     * @return Total cost in diamond equivalent
     */
    public double getDiamondEquivalentCost() {
        return diamondCost +
               (goldCost * 0.5) +
               (ironCost * 0.25) +
               (emeraldCost * 2.0) +
               (experienceCost * 0.1);
    }

    /**
     * Check if this level is a milestone level (unlocks significant features)
     * @return True if this is a milestone level (10, 25, 50, 100, etc.)
     */
    public boolean isMilestoneLevel() {
        return level % 10 == 0 || level == 25 || level == 50 || level == 100 || level == 150;
    }

    /**
     * Get a formatted description of the level benefits
     * @return Formatted benefits description
     */
    public String getBenefitsDescription() {
        StringBuilder description = new StringBuilder();
        description.append("Level ").append(level).append(" Benefits:\n");

        if (claimLimitBonus > 0) {
            description.append("+").append(claimLimitBonus).append(" claim chunks\n");
        }

        if (assistantSlotsBonus > 0) {
            description.append("+").append(assistantSlotsBonus).append(" assistant slots\n");
        }

        if (dailyIncomeBonus > 0) {
            description.append("+§").append(String.format("%.2f", dailyIncomeBonus)).append(" daily income\n");
        }

        if (techPointsReward > 0) {
            description.append("+").append(techPointsReward).append(" tech points\n");
        }

        if (!unlockedPlotTypes.isEmpty()) {
            description.append("Unlocks: ").append(String.join(", ", unlockedPlotTypes)).append("\n");
        }

        return description.toString();
    }

    /**
     * Get a formatted description of the level costs
     * @return Formatted costs description
     */
    public String getCostsDescription() {
        StringBuilder description = new StringBuilder();
        description.append("Level ").append(level).append(" Costs:\n");

        if (diamondCost > 0) {
            description.append(diamondCost).append(" Diamonds\n");
        }

        if (goldCost > 0) {
            description.append(goldCost).append(" Gold Ingots\n");
        }

        if (ironCost > 0) {
            description.append(ironCost).append(" Iron Ingots\n");
        }

        if (emeraldCost > 0) {
            description.append(emeraldCost).append(" Emeralds\n");
        }

        if (experienceCost > 0) {
            description.append(experienceCost).append(" Experience Bottles\n");
        }

        return description.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TownLevel townLevel = (TownLevel) obj;
        return level == townLevel.level;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(level);
    }

    @Override
    public String toString() {
        return "TownLevel{" +
                "level=" + level +
                ", diamondCost=" + diamondCost +
                ", goldCost=" + goldCost +
                ", ironCost=" + ironCost +
                ", emeraldCost=" + emeraldCost +
                ", experienceCost=" + experienceCost +
                ", techPointsReward=" + techPointsReward +
                ", claimLimitBonus=" + claimLimitBonus +
                ", assistantSlotsBonus=" + assistantSlotsBonus +
                ", dailyIncomeBonus=" + dailyIncomeBonus +
                ", unlockedPlotTypes=" + unlockedPlotTypes +
                '}';
    }
}