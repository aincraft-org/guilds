package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a guild level definition with costs and benefits
 */
public class GuildLevel {

    /** The level. */
    private int level;
    /** The resource costs. */
    private Map<String, Integer> resourceCosts; // Material name -> quantity
    /** The tech points reward. */
    private int techPointsReward;
    /** The claim limit bonus. */
    private int claimLimitBonus;
    /** The assistant slots bonus. */
    private int assistantSlotsBonus;
    /** The daily income bonus. */
    private double dailyIncomeBonus;
    /** The unlocked plot types. */
    private List<String> unlockedPlotTypes;
    /** The created at. */
    private LocalDateTime createdAt;

    /**
     * Default constructor for database mapping
     */
    public GuildLevel() {
        this.createdAt = LocalDateTime.now();
        this.unlockedPlotTypes = List.of();
        this.resourceCosts = new HashMap<>();
    }

    /**
     * Constructor for creating a new guild level
     */
    public GuildLevel(int level, Map<String, Integer> resourceCosts, int techPointsReward,
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
    /**
     * Returns the level.
     * @return the result
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the level.
     * @param level the level
     */
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    /**
     * Returns the resource costs.
     * @return the result
     */
    public Map<String, Integer> getResourceCosts() {
        return new HashMap<>(resourceCosts);
    }

    /**
     * Sets the resource costs.
     * @param resourceCosts the resource costs
     */
    public void setResourceCosts(Map<String, Integer> resourceCosts) {
        this.resourceCosts = resourceCosts != null ? new HashMap<>(resourceCosts) : new HashMap<>();
    }

    /**
     * Returns the tech points reward.
     * @return the result
     */
    public int getTechPointsReward() {
        return techPointsReward;
    }

    /**
     * Sets the tech points reward.
     * @param techPointsReward the tech points reward
     */
    public void setTechPointsReward(int techPointsReward) {
        this.techPointsReward = Math.max(0, techPointsReward);
    }

    /**
     * Returns the claim limit bonus.
     * @return the result
     */
    public int getClaimLimitBonus() {
        return claimLimitBonus;
    }

    /**
     * Sets the claim limit bonus.
     * @param claimLimitBonus the claim limit bonus
     */
    public void setClaimLimitBonus(int claimLimitBonus) {
        this.claimLimitBonus = Math.max(0, claimLimitBonus);
    }

    /**
     * Returns the assistant slots bonus.
     * @return the result
     */
    public int getAssistantSlotsBonus() {
        return assistantSlotsBonus;
    }

    /**
     * Sets the assistant slots bonus.
     * @param assistantSlotsBonus the assistant slots bonus
     */
    public void setAssistantSlotsBonus(int assistantSlotsBonus) {
        this.assistantSlotsBonus = Math.max(0, assistantSlotsBonus);
    }

    /**
     * Returns the daily income bonus.
     * @return the result
     */
    public double getDailyIncomeBonus() {
        return dailyIncomeBonus;
    }

    /**
     * Sets the daily income bonus.
     * @param dailyIncomeBonus the daily income bonus
     */
    public void setDailyIncomeBonus(double dailyIncomeBonus) {
        this.dailyIncomeBonus = Math.max(0.0, dailyIncomeBonus);
    }

    /**
     * Returns the unlocked plot types.
     * @return the result
     */
    public List<String> getUnlockedPlotTypes() {
        return unlockedPlotTypes;
    }

    /**
     * Sets the unlocked plot types.
     * @param unlockedPlotTypes the unlocked plot types
     */
    public void setUnlockedPlotTypes(List<String> unlockedPlotTypes) {
        this.unlockedPlotTypes = unlockedPlotTypes != null ? unlockedPlotTypes : List.of();
    }

    /**
     * Returns the created at.
     * @return the result
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created at.
     * @param createdAt the created at
     */
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

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GuildLevel guildLevel = (GuildLevel) obj;
        return level == guildLevel.level;
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return Integer.hashCode(level);
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "GuildLevel{" +
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
