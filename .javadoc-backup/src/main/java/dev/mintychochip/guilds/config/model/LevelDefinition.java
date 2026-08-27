package dev.mintychochip.guilds.config.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A guild level definition parsed from config.yml under {@code guild_levels.levels}.
 *
 * <p>Definition levels go from 1 (starting level) to {@code guild_levels.max_level}.
 * Level 1 has no requirements; higher levels list material requirements and benefits.
 */
public class LevelDefinition {

    /** The level. */
    private int level;
    /** The requirements. */
    private Map<String, Integer> requirements = new HashMap<>();
    /** The tech points. */
    private int techPoints;
    /** The claim limit bonus. */
    private int claimLimitBonus;
    /** The assistant slots bonus. */
    private int assistantSlotsBonus;
    /** The daily income bonus. */
    private double dailyIncomeBonus;
    /** The unlocked plot types. */
    private List<String> unlockedPlotTypes = new ArrayList<>();

    /** Creates a new level definition instance. */
    public LevelDefinition() {
    }

    /**
     * Creates a new level definition instance.
     * @param level the level
     * @param requirements the requirements
     * @param techPoints the tech points
     * @param claimLimitBonus the claim limit bonus
     * @param assistantSlotsBonus the assistant slots bonus
     * @param dailyIncomeBonus the daily income bonus
     * @param unlockedPlotTypes the unlocked plot types
     */
    public LevelDefinition(int level, Map<String, Integer> requirements, int techPoints,
                           int claimLimitBonus, int assistantSlotsBonus, double dailyIncomeBonus,
                           List<String> unlockedPlotTypes) {
        this.level = level;
        this.requirements = requirements != null ? new HashMap<>(requirements) : new HashMap<>();
        this.techPoints = techPoints;
        this.claimLimitBonus = claimLimitBonus;
        this.assistantSlotsBonus = assistantSlotsBonus;
        this.dailyIncomeBonus = dailyIncomeBonus;
        this.unlockedPlotTypes = unlockedPlotTypes != null ? new ArrayList<>(unlockedPlotTypes) : new ArrayList<>();
    }

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
        this.level = level;
    }

    /**
     * Returns the requirements.
     * @return the result
     */
    public Map<String, Integer> getRequirements() {
        return requirements;
    }

    /**
     * Sets the requirements.
     * @param requirements the requirements
     */
    public void setRequirements(Map<String, Integer> requirements) {
        this.requirements = requirements != null ? new HashMap<>(requirements) : new HashMap<>();
    }

    /**
     * Returns the tech points.
     * @return the result
     */
    public int getTechPoints() {
        return techPoints;
    }

    /**
     * Sets the tech points.
     * @param techPoints the tech points
     */
    public void setTechPoints(int techPoints) {
        this.techPoints = techPoints;
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
        this.claimLimitBonus = claimLimitBonus;
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
        this.assistantSlotsBonus = assistantSlotsBonus;
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
        this.dailyIncomeBonus = dailyIncomeBonus;
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
        this.unlockedPlotTypes = unlockedPlotTypes != null ? new ArrayList<>(unlockedPlotTypes) : new ArrayList<>();
    }
}
