package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a town level definition with costs and benefits
 */
public class TownLevel {

    private int level;
    private Map<String, Integer> resourceCosts; // Material name -> quantity
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
        this.resourceCosts = new HashMap<>();
    }

    /**
     * Constructor for creating a new town level
     */
    public TownLevel(int level, Map<String, Integer> resourceCosts, int techPointsReward,
                     int claimLimitBonus, int assistantSlotsBonus, double dailyIncomeBonus,
                     List<String> unlockedPlotTypes) {
        this();
        this.level = level;
        this.resourceCosts = resourceCosts != null ? new HashMap<>(resourceCosts) : new HashMap<>();
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

    public Map<String, Integer> getResourceCosts() {
        return new HashMap<>(resourceCosts);
    }

    public void setResourceCosts(Map<String, Integer> resourceCosts) {
        this.resourceCosts = resourceCosts != null ? new HashMap<>(resourceCosts) : new HashMap<>();
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
     * Get the cost for a specific resource/material
     * @param materialName Material name (e.g., "DIAMOND", "GOLD_INGOT")
     * @return Cost amount, or 0 if not required
     */
    public int getResourceCost(String materialName) {
        if (materialName == null || resourceCosts == null) return 0;
        return resourceCosts.getOrDefault(materialName.toUpperCase(), 0);
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
        return resourceCosts != null && !resourceCosts.isEmpty();
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

        if (resourceCosts != null && !resourceCosts.isEmpty()) {
            for (Map.Entry<String, Integer> entry : resourceCosts.entrySet()) {
                String materialName = formatMaterialName(entry.getKey());
                description.append(entry.getValue()).append(" ").append(materialName).append("\n");
            }
        }

        return description.toString();
    }

    /**
     * Format material name for display (GOLD_INGOT -> Gold Ingot)
     */
    private String formatMaterialName(String materialName) {
        if (materialName == null) return "";
        return Arrays.stream(materialName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(materialName);
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
                ", resourceCosts=" + resourceCosts +
                ", techPointsReward=" + techPointsReward +
                ", claimLimitBonus=" + claimLimitBonus +
                ", assistantSlotsBonus=" + assistantSlotsBonus +
                ", dailyIncomeBonus=" + dailyIncomeBonus +
                ", unlockedPlotTypes=" + unlockedPlotTypes +
                '}';
    }
}
